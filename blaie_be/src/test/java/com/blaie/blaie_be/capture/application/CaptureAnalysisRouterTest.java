package com.blaie.blaie_be.capture.application;

import com.blaie.blaie_be.capture.application.port.ImageAnalysisInput;
import com.blaie.blaie_be.capture.application.port.ImageAnalyzerPort;
import com.blaie.blaie_be.capture.application.port.ObjectStoragePort;
import com.blaie.blaie_be.capture.application.port.TextClassifierPort;
import com.blaie.blaie_be.capture.application.result.ProcessingAssetResult;
import com.blaie.blaie_be.capture.application.result.ProcessingJobResult;
import com.blaie.blaie_be.capture.domain.CaptureAnalysis;
import com.blaie.blaie_be.capture.domain.CaptureCategory;
import com.blaie.blaie_be.capture.domain.CaptureInputType;
import com.blaie.blaie_be.capture.domain.CapturePiiMode;
import com.blaie.blaie_be.capture.domain.ClassifiedTextItem;
import com.blaie.blaie_be.capture.domain.ProcessingJobStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaptureAnalysisRouterTest {
    private final TextClassifierPort textClassifier = mock(TextClassifierPort.class);
    private final ImageAnalyzerPort imageAnalyzer = mock(ImageAnalyzerPort.class);
    private final ObjectStoragePort storage = mock(ObjectStoragePort.class);
    private final CaptureAnalysisRouter router = new CaptureAnalysisRouter(
            textClassifier,
            imageAnalyzer,
            storage,
            new CaptureContentPolicy(),
            new CapturePiiPolicy(() -> CapturePiiMode.MASK_STRUCTURED)
    );

    @Test
    void textJobUsesOnlyDeepSeekCapability() {
        ProcessingJobResult job = job("text_classification", CaptureInputType.TEXT, "Buy milk", List.of());
        CaptureAnalysis expected = analysis("deepseek");
        when(textClassifier.classify("Buy milk")).thenReturn(expected);

        assertThat(router.analyze(job)).isSameAs(expected);

        verify(imageAnalyzer, never()).analyze(any());
        verify(storage, never()).get(any());
    }

    @Test
    void imageJobLoadsPrivateObjectAndUsesOnlyGeminiCapability() {
        ProcessingAssetResult asset = new ProcessingAssetResult(
                UUID.randomUUID(),
                "captures/asset.png",
                "image/png"
        );
        ProcessingJobResult job = job(
                "image_analysis",
                CaptureInputType.IMAGE,
                "Read this receipt",
                List.of(asset)
        );
        when(storage.get("captures/asset.png")).thenReturn(new byte[]{1, 2, 3});
        when(imageAnalyzer.analyze(any())).thenReturn(analysis("gemini"));

        CaptureAnalysis result = router.analyze(job);

        assertThat(result.provider()).isEqualTo("gemini");
        verify(textClassifier, never()).classify(any());
        var input = org.mockito.ArgumentCaptor.forClass(ImageAnalysisInput.class);
        verify(imageAnalyzer).analyze(input.capture());
        assertThat(input.getValue().imageBytes()).containsExactly(1, 2, 3);
        assertThat(input.getValue().contentType()).isEqualTo("image/png");
        assertThat(input.getValue().text()).isEqualTo("Read this receipt");
    }

    private ProcessingJobResult job(
            String jobType,
            CaptureInputType inputType,
            String text,
            List<ProcessingAssetResult> assets
    ) {
        return new ProcessingJobResult(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "request-id",
                jobType,
                inputType,
                text,
                assets,
                ProcessingJobStatus.PROCESSING,
                1,
                4,
                0,
                1,
                Instant.parse("2026-07-28T12:00:00Z")
        );
    }

    private CaptureAnalysis analysis(String provider) {
        return new CaptureAnalysis(
                List.of(new ClassifiedTextItem("Buy milk", CaptureCategory.TASK)),
                provider,
                "model",
                "v1"
        );
    }
}
