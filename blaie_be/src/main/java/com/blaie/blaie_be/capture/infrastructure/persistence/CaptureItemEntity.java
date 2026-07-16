package com.blaie.blaie_be.capture.infrastructure.persistence;

import com.blaie.blaie_be.capture.domain.ClassifiedTextItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "capture_items")
@EntityListeners(AuditingEntityListener.class)
public class CaptureItemEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "capture_id", nullable = false)
    private UUID captureId;

    @Column(name = "original_text", nullable = false)
    private String originalText;

    @Column(name = "category", length = 30)
    private String category;

    @Column(name = "processing_status", nullable = false, length = 20)
    private String processingStatus;

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CaptureItemEntity() {
    }

    public static CaptureItemEntity completed(CaptureEntity capture, ClassifiedTextItem classifiedItem) {
        CaptureItemEntity item = new CaptureItemEntity();
        item.id = UUID.randomUUID();
        item.userId = capture.userId();
        item.captureId = capture.id();
        item.originalText = classifiedItem.originalText().trim();
        item.category = classifiedItem.category().value();
        item.processingStatus = "completed";
        return item;
    }

    public static CaptureItemEntity failed(CaptureEntity capture) {
        CaptureItemEntity item = new CaptureItemEntity();
        item.id = UUID.randomUUID();
        item.userId = capture.userId();
        item.captureId = capture.id();
        item.originalText = capture.originalText();
        item.category = null;
        item.processingStatus = "failed";
        return item;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public UUID captureId() {
        return captureId;
    }

    public String originalText() {
        return originalText;
    }

    public String category() {
        return category;
    }

    public String processingStatus() {
        return processingStatus;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
