package com.blaie.blaie_be.capture.application;

import com.blaie.blaie_be.authz.application.AuthorizationService;
import com.blaie.blaie_be.authz.domain.PermissionAction;
import com.blaie.blaie_be.capture.application.port.ImageCaptureSettingsPort;
import com.blaie.blaie_be.capture.application.port.ImageCaptureWorkflowStorePort;
import com.blaie.blaie_be.capture.application.port.ObjectStoragePort;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import com.blaie.blaie_be.core.security.CurrentUserHolder;
import java.net.URI;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ImageAssetService {
    private final ImageCaptureWorkflowStorePort workflowStore;
    private final ObjectStoragePort objectStorage;
    private final ImageCaptureSettingsPort settings;
    private final AuthorizationService authorization;

    public ImageAssetService(
            ImageCaptureWorkflowStorePort workflowStore,
            ObjectStoragePort objectStorage,
            ImageCaptureSettingsPort settings,
            AuthorizationService authorization
    ) {
        this.workflowStore = workflowStore;
        this.objectStorage = objectStorage;
        this.settings = settings;
        this.authorization = authorization;
    }

    public URI createOwnedReadUri(UUID captureId, UUID assetId) {
        authorization.require(PermissionAction.CAPTURE_READ);
        UUID userId;
        try {
            userId = UUID.fromString(CurrentUserHolder.requireCurrentUser().userId());
        } catch (IllegalArgumentException exception) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        var asset = workflowStore.findOwnedAsset(captureId, assetId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.CAPTURE_NOT_FOUND));
        return objectStorage.createReadUri(asset.objectKey(), settings.signedUrlTtl());
    }
}
