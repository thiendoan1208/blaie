package com.blaie.blaie_be.capture.infrastructure.persistence;

import com.blaie.blaie_be.capture.domain.CaptureAnalysis;
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
@Table(name = "captures")
@EntityListeners(AuditingEntityListener.class)
public class CaptureEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "original_text", nullable = false)
    private String originalText;

    @Column(name = "processing_status", nullable = false, length = 20)
    private String processingStatus;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "ai_provider", length = 50)
    private String aiProvider;

    @Column(name = "ai_model", length = 100)
    private String aiModel;

    @Column(name = "prompt_version", length = 50)
    private String promptVersion;

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CaptureEntity() {
    }

    public static CaptureEntity processing(UUID userId, String originalText) {
        CaptureEntity capture = new CaptureEntity();
        capture.id = UUID.randomUUID();
        capture.userId = userId;
        capture.originalText = originalText;
        capture.processingStatus = "processing";
        return capture;
    }

    public void complete(CaptureAnalysis analysis) {
        processingStatus = "completed";
        failureCode = null;
        aiProvider = analysis.provider();
        aiModel = analysis.model();
        promptVersion = analysis.promptVersion();
    }

    public void fail(String safeFailureCode) {
        processingStatus = "failed";
        failureCode = safeFailureCode;
    }

    public void restart() {
        processingStatus = "processing";
        failureCode = null;
        aiProvider = null;
        aiModel = null;
        promptVersion = null;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public String originalText() {
        return originalText;
    }

    public String processingStatus() {
        return processingStatus;
    }

    public String failureCode() {
        return failureCode;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
