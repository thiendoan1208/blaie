package com.blaie.blaie_be.capture.application;

import com.blaie.blaie_be.authz.application.AuthorizationService;
import com.blaie.blaie_be.authz.domain.PermissionAction;
import com.blaie.blaie_be.capture.application.port.CaptureProcessingSettingsPort;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort;
import com.blaie.blaie_be.capture.application.port.CaptureWorkflowStorePort;
import com.blaie.blaie_be.capture.application.result.CaptureResult;
import com.blaie.blaie_be.capture.domain.ProcessingStatus;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import com.blaie.blaie_be.core.security.CurrentUser;
import com.blaie.blaie_be.core.security.CurrentUserHolder;
import com.blaie.blaie_be.core.request.RequestContext;
import com.blaie.blaie_be.core.request.RequestContextHolder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CaptureServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-07-16T12:00:00Z");
    private static final UUID IDEMPOTENCY_KEY = UUID.fromString("6a99337a-65aa-4f19-a2c4-8796434a1ee8");

    @Test
    void captureTextCreatesProcessingWorkflowWithoutCallingAiInTheRequest() {
        UUID userId = UUID.randomUUID();
        InMemoryWorkflowStore workflowStore = new InMemoryWorkflowStore();
        CaptureService service = service(workflowStore);

        RequestContextHolder.set(new RequestContext("capture-request-123", "POST", "/api/v1/captures/text", null));
        CaptureResult result;
        try {
            result = runAs(userId, () -> service.captureText(
                    "  Meeting at 5 PM and go running  ",
                    IDEMPOTENCY_KEY.toString()
            ));
        } finally {
            RequestContextHolder.clear();
        }

        assertThat(result.processingStatus()).isEqualTo(ProcessingStatus.PROCESSING);
        assertThat(result.items()).isEmpty();
        assertThat(workflowStore.userId).isEqualTo(userId);
        assertThat(workflowStore.originalText).isEqualTo("Meeting at 5 PM and go running");
        assertThat(workflowStore.idempotencyKey).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(workflowStore.requestHash).hasSize(64);
        assertThat(workflowStore.originRequestId).isEqualTo("capture-request-123");
        assertThat(workflowStore.now).isEqualTo(NOW);
        assertThat(workflowStore.expiresAt).isEqualTo(NOW.plus(Duration.ofHours(24)));
    }

    @Test
    void captureTextRequiresUuidIdempotencyKey() {
        CaptureService service = service(new InMemoryWorkflowStore());

        assertThatThrownBy(() -> runAs(UUID.randomUUID(), () -> service.captureText("Buy milk", null)))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).errorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);

        assertThatThrownBy(() -> runAs(UUID.randomUUID(), () -> service.captureText("Buy milk", "not-a-uuid")))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).errorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_INVALID);
    }

    @Test
    void captureLookupAlwaysUsesAuthenticatedUser() {
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        InMemoryWorkflowStore workflowStore = new InMemoryWorkflowStore();
        CaptureService service = service(workflowStore);
        CaptureResult created = runAs(ownerId, () -> service.captureText("Private", IDEMPOTENCY_KEY.toString()));

        assertThatThrownBy(() -> runAs(otherId, () -> service.capture(created.id())))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).errorCode())
                .isEqualTo(ErrorCode.CAPTURE_NOT_FOUND);
    }

    @Test
    void idempotencyLookupResolvesOnlyForTheAuthenticatedOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        InMemoryWorkflowStore workflowStore = new InMemoryWorkflowStore();
        CaptureService service = service(workflowStore);
        CaptureResult created = runAs(
                ownerId,
                () -> service.captureText("Recover after refresh", IDEMPOTENCY_KEY.toString())
        );

        assertThat(runAs(ownerId, () -> service.resolveCapture(IDEMPOTENCY_KEY.toString())).id())
                .isEqualTo(created.id());
        assertThatThrownBy(() -> runAs(otherId, () -> service.resolveCapture(IDEMPOTENCY_KEY.toString())))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).errorCode())
                .isEqualTo(ErrorCode.CAPTURE_NOT_FOUND);
    }

    @Test
    void disabledAsyncAcceptanceRejectsWritesBeforeCallingWorkflowStore() {
        UUID userId = UUID.randomUUID();
        InMemoryWorkflowStore workflowStore = new InMemoryWorkflowStore();
        CaptureService service = service(workflowStore, false);

        assertThatThrownBy(() -> runAs(userId, () -> service.captureText(
                "Buy milk",
                IDEMPOTENCY_KEY.toString()
        )))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).errorCode())
                .isEqualTo(ErrorCode.CAPTURE_PROCESSING_UNAVAILABLE);
        assertThatThrownBy(() -> runAs(userId, () -> service.retry(UUID.randomUUID())))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).errorCode())
                .isEqualTo(ErrorCode.CAPTURE_PROCESSING_UNAVAILABLE);

        assertThat(workflowStore.startCalls).isZero();
        assertThat(workflowStore.retryCalls).isZero();
    }

    @Test
    void successfulUserRetryIncrementsManualRetryTelemetry() {
        UUID userId = UUID.randomUUID();
        InMemoryWorkflowStore workflowStore = new InMemoryWorkflowStore();
        CaptureTelemetryPort telemetry = mock(CaptureTelemetryPort.class);
        CaptureService service = service(
                workflowStore,
                true,
                telemetry
        );
        CaptureResult capture = runAs(
                userId,
                () -> service.captureText("Retry me", IDEMPOTENCY_KEY.toString())
        );

        runAs(userId, () -> service.retry(capture.id()));

        verify(telemetry).incrementRetry(CaptureTelemetryPort.RetrySource.MANUAL);
    }

    @Test
    void sensitiveContentIsRejectedBeforePersistence() {
        InMemoryWorkflowStore workflowStore = new InMemoryWorkflowStore();
        CaptureService service = service(workflowStore);

        assertThatThrownBy(() -> runAs(UUID.randomUUID(), () -> service.captureText(
                "Store sk-abcdefghijklmnopqrstuvwxyz123456",
                IDEMPOTENCY_KEY.toString()
        )))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CAPTURE_SENSITIVE_CONTENT));
        assertThat(workflowStore.startCalls).isZero();
    }

    @Test
    void deleteAlwaysUsesAuthenticatedOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        InMemoryWorkflowStore workflowStore = new InMemoryWorkflowStore();
        CaptureService service = service(workflowStore);
        CaptureResult created = runAs(ownerId, () -> service.captureText("Private", IDEMPOTENCY_KEY.toString()));

        assertThatThrownBy(() -> runAs(otherId, () -> {
            service.delete(created.id());
            return null;
        })).isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.CAPTURE_NOT_FOUND));

        runAs(ownerId, () -> {
            service.delete(created.id());
            return null;
        });
        assertThat(workflowStore.findOwned(created.id(), ownerId)).isEmpty();
    }

    @Test
    void everyUserOperationRequiresItsExplicitPermissionBeforeUsingOwnedStores() {
        UUID userId = UUID.randomUUID();
        AuthorizationService authorization = mock(AuthorizationService.class);
        InMemoryWorkflowStore workflowStore = new InMemoryWorkflowStore();
        CaptureService service = service(
                workflowStore,
                true,
                mock(CaptureTelemetryPort.class),
                authorization
        );

        CaptureResult capture = runAs(userId, () ->
                service.captureText("Authorized capture", IDEMPOTENCY_KEY.toString()));
        runAs(userId, () -> service.capture(capture.id()));
        runAs(userId, () -> service.resolveCapture(IDEMPOTENCY_KEY.toString()));
        runAs(userId, () -> service.processingCaptures(20));
        runAs(userId, () -> service.retry(capture.id()));
        runAs(userId, () -> {
            service.delete(capture.id());
            return null;
        });

        verify(authorization).require(PermissionAction.CAPTURE_CREATE);
        verify(authorization, times(3)).require(PermissionAction.CAPTURE_READ);
        verify(authorization).require(PermissionAction.CAPTURE_UPDATE);
        verify(authorization).require(PermissionAction.CAPTURE_DELETE);
    }

    private CaptureService service(CaptureWorkflowStorePort workflowStore) {
        return service(workflowStore, true);
    }

    private CaptureService service(
            CaptureWorkflowStorePort workflowStore,
            boolean acceptAsyncEnabled
    ) {
        return service(
                workflowStore,
                acceptAsyncEnabled,
                mock(CaptureTelemetryPort.class)
        );
    }

    private CaptureService service(
            CaptureWorkflowStorePort workflowStore,
            boolean acceptAsyncEnabled,
            CaptureTelemetryPort telemetry
    ) {
        return service(
                workflowStore,
                acceptAsyncEnabled,
                telemetry,
                mock(AuthorizationService.class)
        );
    }

    private CaptureService service(
            CaptureWorkflowStorePort workflowStore,
            boolean acceptAsyncEnabled,
            CaptureTelemetryPort telemetry,
            AuthorizationService authorization
    ) {
        CaptureProcessingSettingsPort settings = new CaptureProcessingSettingsPort() {
            @Override
            public boolean acceptAsyncEnabled() {
                return acceptAsyncEnabled;
            }

            @Override
            public int maxAttempts() {
                return 4;
            }

            @Override
            public Duration idempotencyTtl() {
                return Duration.ofHours(24);
            }

            @Override
            public Duration leaseDuration() {
                return Duration.ofSeconds(30);
            }

            @Override
            public Duration heartbeatInterval() {
                return Duration.ofSeconds(10);
            }

            @Override
            public Duration retryDelay(int failedAttemptCount) {
                return Duration.ofSeconds(2);
            }

            @Override
            public Duration dispatchRetryDelay(int dispatchGeneration) {
                return Duration.ofSeconds(30);
            }

            @Override
            public int maxActiveJobsPerUser() {
                return 10;
            }

            @Override
            public int maxActiveJobsTotal() {
                return 1_000;
            }

            @Override
            public Duration maxOldestQueuedAge() {
                return Duration.ofMinutes(5);
            }

            @Override
            public Duration admissionRetryAfter() {
                return Duration.ofSeconds(30);
            }
        };
        return new CaptureServiceImpl(
                workflowStore,
                settings,
                Clock.fixed(NOW, ZoneOffset.UTC),
                telemetry,
                new CaptureContentPolicy(),
                authorization
        );
    }

    private <T> T runAs(UUID userId, java.util.function.Supplier<T> supplier) {
        return CurrentUserHolder.runAs(new CurrentUser(userId.toString(), false, Set.of()), supplier);
    }

    private static final class InMemoryWorkflowStore implements CaptureWorkflowStorePort {
        private final List<OwnedCapture> captures = new ArrayList<>();
        private UUID userId;
        private String originalText;
        private UUID idempotencyKey;
        private String requestHash;
        private String originRequestId;
        private Instant now;
        private Instant expiresAt;
        private int startCalls;
        private int retryCalls;

        @Override
        public CaptureResult startTextCapture(
                UUID userId,
                String originalText,
                UUID idempotencyKey,
                String requestHash,
                String originRequestId,
                Instant now,
                Instant idempotencyExpiresAt,
                int maxAttempts
        ) {
            startCalls++;
            this.userId = userId;
            this.originalText = originalText;
            this.idempotencyKey = idempotencyKey;
            this.requestHash = requestHash;
            this.originRequestId = originRequestId;
            this.now = now;
            this.expiresAt = idempotencyExpiresAt;
            CaptureResult result = new CaptureResult(
                    UUID.randomUUID(),
                    originalText,
                    ProcessingStatus.PROCESSING,
                    null,
                    false,
                    List.of(),
                    now,
                    now
            );
            captures.add(new OwnedCapture(userId, idempotencyKey, result));
            return result;
        }

        @Override
        public Optional<CaptureResult> findOwned(UUID captureId, UUID userId) {
            return captures.stream()
                    .filter(capture -> capture.userId().equals(userId) && capture.result().id().equals(captureId))
                    .map(OwnedCapture::result)
                    .findFirst();
        }

        @Override
        public Optional<CaptureResult> findOwnedByIdempotencyKey(
                UUID idempotencyKey,
                UUID userId,
                Instant now
        ) {
            return captures.stream()
                    .filter(capture -> capture.userId().equals(userId)
                            && capture.idempotencyKey().equals(idempotencyKey))
                    .map(OwnedCapture::result)
                    .findFirst();
        }

        @Override
        public List<CaptureResult> findOwnedProcessing(UUID userId, int limit) {
            return captures.stream()
                    .filter(capture -> capture.userId().equals(userId))
                    .limit(limit)
                    .map(OwnedCapture::result)
                    .toList();
        }

        @Override
        public CaptureResult retryOwned(UUID captureId, UUID userId, Instant now) {
            retryCalls++;
            return findOwned(captureId, userId).orElseThrow();
        }

        @Override
        public void deleteOwned(UUID captureId, UUID userId) {
            boolean removed = captures.removeIf(capture ->
                    capture.userId().equals(userId) && capture.result().id().equals(captureId));
            if (!removed) throw new AppException(ErrorCode.CAPTURE_NOT_FOUND);
        }
    }

    private record OwnedCapture(UUID userId, UUID idempotencyKey, CaptureResult result) {
    }

}
