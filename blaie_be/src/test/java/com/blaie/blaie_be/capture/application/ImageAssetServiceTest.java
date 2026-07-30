package com.blaie.blaie_be.capture.application;

import com.blaie.blaie_be.authz.application.AuthorizationService;
import com.blaie.blaie_be.authz.domain.PermissionAction;
import com.blaie.blaie_be.capture.application.port.ImageCaptureSettingsPort;
import com.blaie.blaie_be.capture.application.port.ImageCaptureWorkflowStorePort;
import com.blaie.blaie_be.capture.application.port.ObjectStoragePort;
import com.blaie.blaie_be.capture.application.result.OwnedCaptureAssetResult;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import com.blaie.blaie_be.core.security.CurrentUser;
import com.blaie.blaie_be.core.security.CurrentUserHolder;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageAssetServiceTest {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CAPTURE_ID = UUID.randomUUID();
    private static final UUID ASSET_ID = UUID.randomUUID();

    private ImageCaptureWorkflowStorePort workflowStore;
    private ObjectStoragePort storage;
    private AuthorizationService authorization;
    private ImageAssetService service;

    @BeforeEach
    void setUp() {
        CurrentUserHolder.set(new CurrentUser(
                USER_ID.toString(),
                false,
                Set.of(PermissionAction.CAPTURE_READ.key())
        ));
        workflowStore = mock(ImageCaptureWorkflowStorePort.class);
        storage = mock(ObjectStoragePort.class);
        authorization = mock(AuthorizationService.class);
        ImageCaptureSettingsPort settings = new ImageCaptureSettingsPort() {
            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public Duration signedUrlTtl() {
                return Duration.ofMinutes(2);
            }
        };
        service = new ImageAssetService(
                workflowStore,
                storage,
                settings,
                authorization
        );
    }

    @AfterEach
    void clearUser() {
        CurrentUserHolder.clear();
    }

    @Test
    void signsOnlyAnAssetResolvedThroughCurrentUserOwnership() {
        when(workflowStore.findOwnedAsset(CAPTURE_ID, ASSET_ID, USER_ID))
                .thenReturn(Optional.of(new OwnedCaptureAssetResult(
                        ASSET_ID,
                        CAPTURE_ID,
                        "captures/private.png"
                )));
        URI signed = URI.create("https://signed.example/private");
        when(storage.createReadUri("captures/private.png", Duration.ofMinutes(2)))
                .thenReturn(signed);

        assertThat(service.createOwnedReadUri(CAPTURE_ID, ASSET_ID)).isEqualTo(signed);

        verify(authorization).require(PermissionAction.CAPTURE_READ);
        verify(workflowStore).findOwnedAsset(CAPTURE_ID, ASSET_ID, USER_ID);
    }

    @Test
    void foreignOrMissingAssetUsesTheSameNotFoundAndNeverCreatesASignedUrl() {
        when(workflowStore.findOwnedAsset(CAPTURE_ID, ASSET_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createOwnedReadUri(CAPTURE_ID, ASSET_ID))
                .isInstanceOf(AppException.class)
                .satisfies(error -> assertThat(((AppException) error).errorCode())
                        .isEqualTo(ErrorCode.CAPTURE_NOT_FOUND));

        verify(storage, never()).createReadUri(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
    }
}
