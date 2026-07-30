package com.blaie.blaie_be.capture.infrastructure.storage;

import com.blaie.blaie_be.core.error.AppException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class R2ObjectStorageAdapterIntegrationTest {
    private static final String ACCESS_KEY = "blaie-test";
    private static final String SECRET_KEY = "blaie-test-secret";
    private static final String BUCKET = "private-images";

    @Container
    private static final GenericContainer<?> MINIO =
            new GenericContainer<>(DockerImageName.parse(
                    "minio/minio:RELEASE.2025-09-07T16-13-09Z"
            ))
                    .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
                    .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
                    .withCommand("server", "/data")
                    .withExposedPorts(9000)
                    .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    private static R2ObjectStorageAdapter adapter;

    @BeforeAll
    static void createPrivateBucket() {
        URI endpoint = URI.create(
                "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000)
        );
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)
        );
        try (S3Client setup = S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.US_EAST_1)
                .credentialsProvider(credentials)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build()) {
            setup.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        }

        R2Properties properties = new R2Properties();
        properties.setEndpoint(endpoint);
        properties.setRegion(Region.US_EAST_1.id());
        properties.setBucket(BUCKET);
        properties.setAccessKeyId(ACCESS_KEY);
        properties.setSecretAccessKey(SECRET_KEY);
        adapter = new R2ObjectStorageAdapter(properties);
    }

    @Test
    void roundTripsPrivateObjectThroughS3CompatibleApiAndPresignedRead() throws Exception {
        String objectKey = "captures/integration.png";
        byte[] content = new byte[] {1, 2, 3, 4};

        adapter.put(objectKey, content, "image/png");
        assertThat(adapter.exists(objectKey)).isTrue();
        assertThat(adapter.list("captures/", null, 100).objects())
                .extracting(com.blaie.blaie_be.capture.application.port.StoredObject::objectKey)
                .contains(objectKey);

        URI unsignedUri = URI.create(
                "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000)
                        + "/" + BUCKET + "/" + objectKey
        );
        HttpClient http = HttpClient.newHttpClient();
        HttpResponse<Void> unsigned = http.send(
                HttpRequest.newBuilder(unsignedUri).GET().build(),
                HttpResponse.BodyHandlers.discarding()
        );
        assertThat(unsigned.statusCode()).isEqualTo(403);

        assertThat(adapter.get(objectKey)).containsExactly(content);
        URI signedUri = adapter.createReadUri(objectKey, Duration.ofMinutes(2));
        HttpResponse<byte[]> signed = http.send(
                HttpRequest.newBuilder(signedUri).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );
        assertThat(signed.statusCode()).isEqualTo(200);
        assertThat(signed.body()).containsExactly(content);

        adapter.delete(objectKey);
        assertThat(adapter.exists(objectKey)).isFalse();
        assertThatThrownBy(() -> adapter.get(objectKey))
                .isInstanceOf(AppException.class);
    }
}
