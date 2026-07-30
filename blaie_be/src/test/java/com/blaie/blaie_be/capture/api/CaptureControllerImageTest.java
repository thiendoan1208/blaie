package com.blaie.blaie_be.capture.api;

import com.blaie.blaie_be.capture.application.CaptureService;
import com.blaie.blaie_be.capture.application.ImageCaptureService;
import com.blaie.blaie_be.capture.application.ImageAssetService;
import com.blaie.blaie_be.capture.application.port.ImageInput;
import com.blaie.blaie_be.capture.application.result.CaptureAssetResult;
import com.blaie.blaie_be.capture.application.result.CaptureResult;
import com.blaie.blaie_be.capture.domain.CaptureInputType;
import com.blaie.blaie_be.capture.domain.ProcessingStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaptureControllerImageTest {
    @Test
    void acceptsImageOnlyMultipartCaptureAndReturnsAttachmentMetadata() throws Exception {
        CaptureService captureService = mock(CaptureService.class);
        ImageCaptureService imageCaptureService = mock(ImageCaptureService.class);
        CaptureController controller = new CaptureController(
                captureService,
                imageCaptureService,
                mock(ImageAssetService.class)
        );
        UUID captureId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-28T12:00:00Z");
        when(imageCaptureService.captureImage(
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("idem-key")
        )).thenReturn(new CaptureResult(
                captureId,
                CaptureInputType.IMAGE,
                null,
                ProcessingStatus.PROCESSING,
                null,
                false,
                List.of(new CaptureAssetResult(assetId, "image", "image/png", 3, 1, 1)),
                List.of(),
                now,
                now
        ));
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "receipt.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        var response = controller.captureImage("idem-key", null, image);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        var body = response.getBody().data();
        assertThat(body.inputType()).isEqualTo("image");
        assertThat(body.attachments()).singleElement().satisfies(asset -> {
            assertThat(asset.id()).isEqualTo(assetId);
            assertThat(asset.contentUrl())
                    .isEqualTo("/api/v1/captures/" + captureId + "/assets/" + assetId + "/content");
        });
        ArgumentCaptor<ImageInput> input = ArgumentCaptor.forClass(ImageInput.class);
        verify(imageCaptureService).captureImage(
                org.mockito.ArgumentMatchers.isNull(),
                input.capture(),
                org.mockito.ArgumentMatchers.eq("idem-key")
        );
        assertThat(input.getValue().bytes()).containsExactly(1, 2, 3);
        assertThat(input.getValue().declaredContentType()).isEqualTo("image/png");
        assertThat(input.getValue().originalFilename()).isEqualTo("receipt.png");
    }
}
