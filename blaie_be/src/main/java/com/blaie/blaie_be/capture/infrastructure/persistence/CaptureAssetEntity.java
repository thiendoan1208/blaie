package com.blaie.blaie_be.capture.infrastructure.persistence;

import com.blaie.blaie_be.capture.application.port.CaptureAssetDraft;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "capture_assets")
@EntityListeners(AuditingEntityListener.class)
public class CaptureAssetEntity {
    @Id
    private UUID id;

    @Column(name = "capture_id", nullable = false)
    private UUID captureId;

    @Column(name = "asset_type", nullable = false, length = 20)
    private String assetType;

    @Column(name = "asset_position", nullable = false)
    private int assetPosition;

    @Column(name = "storage_provider", nullable = false, length = 30)
    private String storageProvider;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CaptureAssetEntity() {
    }

    static CaptureAssetEntity image(UUID captureId, CaptureAssetDraft draft) {
        CaptureAssetEntity asset = new CaptureAssetEntity();
        asset.id = draft.id();
        asset.captureId = captureId;
        asset.assetType = "image";
        asset.assetPosition = draft.position();
        asset.storageProvider = draft.storageProvider();
        asset.objectKey = draft.objectKey();
        asset.contentType = draft.contentType();
        asset.sizeBytes = draft.sizeBytes();
        asset.sha256 = draft.sha256();
        asset.width = draft.width();
        asset.height = draft.height();
        return asset;
    }

    public UUID id() {
        return id;
    }

    public UUID captureId() {
        return captureId;
    }

    public String assetType() {
        return assetType;
    }

    public String objectKey() {
        return objectKey;
    }

    public String contentType() {
        return contentType;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }
}
