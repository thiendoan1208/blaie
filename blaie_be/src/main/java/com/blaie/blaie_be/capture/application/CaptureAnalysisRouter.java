package com.blaie.blaie_be.capture.application;

import com.blaie.blaie_be.capture.application.port.ImageAnalysisInput;
import com.blaie.blaie_be.capture.application.port.ImageAnalyzerPort;
import com.blaie.blaie_be.capture.application.port.ObjectStoragePort;
import com.blaie.blaie_be.capture.application.port.TextClassifierPort;
import com.blaie.blaie_be.capture.application.result.ProcessingAssetResult;
import com.blaie.blaie_be.capture.application.result.ProcessingJobResult;
import com.blaie.blaie_be.capture.domain.CaptureAnalysis;
import com.blaie.blaie_be.capture.domain.CaptureAnalysisException;
import com.blaie.blaie_be.capture.domain.CaptureFailureClass;
import org.springframework.stereotype.Component;

@Component
public class CaptureAnalysisRouter {
    private final TextClassifierPort textClassifier;
    private final ImageAnalyzerPort imageAnalyzer;
    private final ObjectStoragePort objectStorage;
    private final CaptureContentPolicy contentPolicy;
    private final CapturePiiPolicy piiPolicy;
    public CaptureAnalysisRouter(
            TextClassifierPort textClassifier,
            ImageAnalyzerPort imageAnalyzer,
            ObjectStoragePort objectStorage,
            CaptureContentPolicy contentPolicy,
            CapturePiiPolicy piiPolicy
    ) {
        this.textClassifier = textClassifier;
        this.imageAnalyzer = imageAnalyzer;
        this.objectStorage = objectStorage;
        this.contentPolicy = contentPolicy;
        this.piiPolicy = piiPolicy;
    }

    public CaptureAnalysis analyze(ProcessingJobResult job) {
        CaptureAnalysis analysis = switch (job.jobType()) {
            case "text_classification" -> analyzeText(job.originalText());
            case "image_analysis" -> analyzeImage(job);
            default -> throw terminal("unsupported_job_type", "Capture job type is unsupported");
        };
        analysis.items().forEach(item -> contentPolicy.validate(item.originalText()));
        return analysis;
    }

    private CaptureAnalysis analyzeText(String text) {
        contentPolicy.validate(text);
        CapturePiiPolicy.PreparedText prepared = piiPolicy.prepare(text);
        return piiPolicy.restore(prepared, textClassifier.classify(prepared.providerText()));
    }

    private CaptureAnalysis analyzeImage(ProcessingJobResult job) {
        if (job.assets().size() != 1) {
            throw terminal("image_asset_missing", "Image capture asset is missing");
        }
        ProcessingAssetResult asset = job.assets().getFirst();
        byte[] bytes;
        try {
            bytes = objectStorage.get(asset.objectKey());
        } catch (RuntimeException exception) {
            throw new CaptureAnalysisException(
                    "image_storage_unavailable",
                    "Image storage read failed",
                    CaptureFailureClass.SYSTEM_RETRYABLE,
                    exception
            );
        }

        CapturePiiPolicy.PreparedText prepared = null;
        String providerText = null;
        if (job.originalText() != null) {
            contentPolicy.validate(job.originalText());
            prepared = piiPolicy.prepare(job.originalText());
            providerText = prepared.providerText();
        }
        CaptureAnalysis analysis = imageAnalyzer.analyze(new ImageAnalysisInput(
                providerText,
                asset.contentType(),
                bytes
        ));
        return prepared == null ? analysis : piiPolicy.restore(prepared, analysis);
    }

    private CaptureAnalysisException terminal(String code, String message) {
        return new CaptureAnalysisException(
                code,
                message,
                CaptureFailureClass.CONTENT_TERMINAL
        );
    }
}
