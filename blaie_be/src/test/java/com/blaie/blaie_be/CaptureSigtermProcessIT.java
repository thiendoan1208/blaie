package com.blaie.blaie_be;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class CaptureSigtermProcessIT {
    private static final Duration CONDITION_TIMEOUT = Duration.ofSeconds(30);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void sigtermDuringProviderCallStopsWithinBoundAndNextProcessRecoversLease() throws Exception {
        Path applicationJar = Path.of("target", "blaie_be-0.0.1-SNAPSHOT.jar").toAbsolutePath();
        assertThat(applicationJar).isRegularFile();
        BlockingDeepSeekServer provider = new BlockingDeepSeekServer();

        try (provider; Network network = Network.newNetwork()) {
            PostgreSQLContainer postgres = new PostgreSQLContainer(
                    DockerImageName.parse("postgres:16-alpine")
            );
            postgres.withDatabaseName("blaie");
            postgres.withUsername("blaie");
            postgres.withPassword("blaie");
            postgres.withNetwork(network);
            postgres.withNetworkAliases("postgres");

            GenericContainer<?> redis = new GenericContainer<>(
                    DockerImageName.parse("redis:7-alpine")
            );
            redis.withExposedPorts(6379);
            redis.withNetwork(network);
            redis.withNetworkAliases("redis");

            try (postgres; redis) {
                postgres.start();
                redis.start();
                Testcontainers.exposeHostPorts(provider.port());

                ImageFromDockerfile applicationImage = new ImageFromDockerfile(
                        "blaie-capture-sigterm-it-" + UUID.randomUUID(),
                        true
                )
                        .withFileFromPath("application.jar", applicationJar)
                        .withDockerfileFromBuilder(builder -> builder
                                .from("eclipse-temurin:25-jre")
                                .copy("application.jar", "/application.jar")
                                .entryPoint("java", "-jar", "/application.jar")
                                .build());
                Map<String, String> environment = applicationEnvironment(provider.port());

                String captureId;
                try (GenericContainer<?> firstApplication = application(
                        applicationImage,
                        network,
                        environment,
                        "sigterm-first"
                )) {
                    firstApplication.start();
                    captureId = submitVerifiedCapture(firstApplication, postgres);
                    assertThat(provider.awaitFirstRequest()).isTrue();
                    awaitDatabaseValue(
                            postgres,
                            "select processing_status from captures where id = ?",
                            captureId,
                            "processing"
                    );

                    long stopStartedAt = System.nanoTime();
                    firstApplication.getDockerClient()
                            .stopContainerCmd(firstApplication.getContainerId())
                            .withTimeout(5)
                            .exec();
                    long stopMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stopStartedAt);
                    Long exitCode = firstApplication.getDockerClient()
                            .inspectContainerCmd(firstApplication.getContainerId())
                            .exec()
                            .getState()
                            .getExitCodeLong();

                    assertThat(stopMillis).isBetween(1_500L, 4_500L);
                    assertThat(exitCode).isNotEqualTo(137L);
                }

                provider.release();
                try (GenericContainer<?> secondApplication = application(
                        applicationImage,
                        network,
                        environment,
                        "sigterm-recovery"
                )) {
                    secondApplication.start();
                    awaitDatabaseValue(
                            postgres,
                            "select processing_status from captures where id = ?",
                            captureId,
                            "completed"
                    );
                    assertThat(queryInt(
                            postgres,
                            "select count(*) from capture_items where capture_id = ?",
                            captureId
                    )).isEqualTo(1);
                    assertThat(provider.attempts()).isGreaterThanOrEqualTo(2);
                }
            }
        }
    }

    private GenericContainer<?> application(
            ImageFromDockerfile image,
            Network network,
            Map<String, String> environment,
            String consumerName
    ) {
        GenericContainer<?> application = new GenericContainer<>(image);
        application.withNetwork(network);
        application.withExposedPorts(8080, 8081);
        application.withEnv(environment);
        application.withEnv("BLAIE_CAPTURE_CONSUMER_NAME", consumerName);
        application.waitingFor(Wait.forHttp("/actuator/health")
                .forPort(8081)
                .forStatusCode(200)
                .withStartupTimeout(Duration.ofSeconds(60)));
        return application;
    }

    private Map<String, String> applicationEnvironment(int providerPort) {
        return Map.ofEntries(
                Map.entry("SPRING_DOCKER_COMPOSE_ENABLED", "false"),
                Map.entry("SPRING_DATASOURCE_URL", "jdbc:postgresql://postgres:5432/blaie"),
                Map.entry("SPRING_DATASOURCE_USERNAME", "blaie"),
                Map.entry("SPRING_DATASOURCE_PASSWORD", "blaie"),
                Map.entry("SPRING_DATA_REDIS_HOST", "redis"),
                Map.entry("SPRING_DATA_REDIS_PORT", "6379"),
                Map.entry("BLAIE_AUTH_ACCESS_TOKEN_SECRET", "sigterm-process-test-secret-at-least-32-bytes"),
                Map.entry("BLAIE_AUTH_COOKIE_SECURE", "false"),
                Map.entry("BLAIE_EMAIL_PROVIDER", "log"),
                Map.entry("BLAIE_EMAIL_FROM", "Blaie <no-reply@test.local>"),
                Map.entry("BLAIE_WEB_BASE_URL", "http://localhost:3000"),
                Map.entry("BLAIE_API_BASE_URL", "http://localhost:8080/api/v1"),
                Map.entry("BLAIE_EMAIL_VERIFICATION_TTL", "24h"),
                Map.entry("GOOGLE_OAUTH_CLIENT_ID", "sigterm-test-google-client"),
                Map.entry("GOOGLE_OAUTH_CLIENT_SECRET", "sigterm-test-google-secret"),
                Map.entry("BLAIE_DEEPSEEK_API_KEY", "sigterm-test-api-key"),
                Map.entry("BLAIE_DEEPSEEK_BASE_URL", "http://host.testcontainers.internal:" + providerPort),
                Map.entry("BLAIE_DEEPSEEK_TIMEOUT", "30s"),
                Map.entry("BLAIE_AI_CONCURRENCY_ENABLED", "false"),
                Map.entry("BLAIE_RATE_LIMIT_ENABLED", "false"),
                Map.entry("BLAIE_RETENTION_ENABLED", "false"),
                Map.entry("BLAIE_CAPTURE_METRICS_COLLECTOR_ENABLED", "false"),
                Map.entry("BLAIE_CAPTURE_JOB_LEASE_DURATION", "3s"),
                Map.entry("BLAIE_CAPTURE_JOB_HEARTBEAT_INTERVAL", "1s"),
                Map.entry("BLAIE_CAPTURE_WORKER_SHUTDOWN_AWAIT", "2s"),
                Map.entry("BLAIE_CAPTURE_POLL_INTERVAL", "100ms"),
                Map.entry("BLAIE_CAPTURE_STREAM_READ_BLOCK", "50ms"),
                Map.entry("BLAIE_CAPTURE_RECOVERY_INTERVAL", "200ms"),
                Map.entry("BLAIE_CAPTURE_OUTBOX_RECOVERY_INTERVAL", "200ms"),
                Map.entry("BLAIE_CAPTURE_OUTBOX_RECOVERY_AGE", "200ms"),
                Map.entry("BLAIE_CAPTURE_RETRY_DELAY_1", "100ms"),
                Map.entry("BLAIE_CAPTURE_RETRY_DELAY_2", "200ms"),
                Map.entry("BLAIE_CAPTURE_RETRY_DELAY_3", "300ms"),
                Map.entry("BLAIE_MANAGEMENT_ADDRESS", "0.0.0.0")
        );
    }

    private String submitVerifiedCapture(
            GenericContainer<?> application,
            PostgreSQLContainer postgres
    ) throws Exception {
        String baseUrl = "http://" + application.getHost() + ":" + application.getMappedPort(8080);
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpResponse<String> csrfResponse = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/auth/csrf")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertThat(csrfResponse.statusCode()).isEqualTo(200);
        String csrfToken = OBJECT_MAPPER.readTree(csrfResponse.body())
                .get("data")
                .get("token")
                .asString();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String email = "sigterm-" + suffix + "@example.com";
        HttpResponse<String> registerResponse = client.send(
                jsonRequest(baseUrl + "/api/v1/auth/register", csrfToken, """
                        {"username":"sigterm-%s","email":"%s","displayName":"SIGTERM Test","password":"Password1!"}
                        """.formatted(suffix, email)),
                HttpResponse.BodyHandlers.ofString()
        );
        assertThat(registerResponse.statusCode()).isEqualTo(201);

        try (Connection connection = connection(postgres);
             PreparedStatement statement = connection.prepareStatement(
                     """
                     update auth_identities
                        set email_verified = true
                      where user_id = (select id from users where email_normalized = ?)
                     """
             )) {
            statement.setString(1, email);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }

        HttpResponse<String> captureResponse = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/captures/text"))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .header("X-XSRF-TOKEN", csrfToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"text\":\"Send the shutdown test report\"}"
                        ))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertThat(captureResponse.statusCode()).isEqualTo(202);
        return OBJECT_MAPPER.readTree(captureResponse.body()).get("data").get("id").asString();
    }

    private HttpRequest jsonRequest(String url, String csrfToken, String body) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", csrfToken)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private void awaitDatabaseValue(
            PostgreSQLContainer postgres,
            String sql,
            String id,
            String expected
    ) throws Exception {
        long deadline = System.nanoTime() + CONDITION_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            String actual = queryString(postgres, sql, id);
            if (expected.equals(actual)) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Database value did not become " + expected + " before timeout");
    }

    private String queryString(PostgreSQLContainer postgres, String sql, String id) throws Exception {
        try (Connection connection = connection(postgres);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.fromString(id));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        }
    }

    private int queryInt(PostgreSQLContainer postgres, String sql, String id) throws Exception {
        try (Connection connection = connection(postgres);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.fromString(id));
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getInt(1);
            }
        }
    }

    private Connection connection(PostgreSQLContainer postgres) throws Exception {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        );
    }

    private static final class BlockingDeepSeekServer implements AutoCloseable {
        private static final byte[] SUCCESS_RESPONSE = """
                {"choices":[{"message":{"content":"{\\"items\\":[{\\"text\\":\\"Send the shutdown test report\\",\\"category\\":\\"task\\"}]}"},"finish_reason":"stop"}]}
                """.getBytes(StandardCharsets.UTF_8);
        private final CountDownLatch firstRequest = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger attempts = new AtomicInteger();
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "sigterm-fake-provider");
            thread.setDaemon(true);
            return thread;
        });
        private final HttpServer server;

        private BlockingDeepSeekServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("0.0.0.0", 0), 0);
            server.createContext("/chat/completions", this::handle);
            server.setExecutor(executor);
            server.start();
        }

        int port() {
            return server.getAddress().getPort();
        }

        boolean awaitFirstRequest() throws InterruptedException {
            return firstRequest.await(CONDITION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        }

        int attempts() {
            return attempts.get();
        }

        void release() {
            release.countDown();
        }

        private void handle(HttpExchange exchange) throws IOException {
            exchange.getRequestBody().readAllBytes();
            attempts.incrementAndGet();
            firstRequest.countDown();
            try {
                if (!release.await(CONDITION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    exchange.sendResponseHeaders(503, -1);
                    return;
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                exchange.sendResponseHeaders(503, -1);
                return;
            }
            try {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, SUCCESS_RESPONSE.length);
                exchange.getResponseBody().write(SUCCESS_RESPONSE);
            } catch (IOException ignored) {
                // The first application is intentionally terminated while this response is blocked.
            } finally {
                exchange.close();
            }
        }

        @Override
        public void close() {
            release();
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
