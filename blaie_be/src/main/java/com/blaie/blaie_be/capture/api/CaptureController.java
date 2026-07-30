package com.blaie.blaie_be.capture.api;

import com.blaie.blaie_be.capture.api.request.CreateTextCaptureRequest;
import com.blaie.blaie_be.capture.api.response.CaptureResponse;
import com.blaie.blaie_be.capture.application.CaptureService;
import com.blaie.blaie_be.capture.application.ImageCaptureService;
import com.blaie.blaie_be.capture.application.ImageAssetService;
import com.blaie.blaie_be.capture.application.port.ImageInput;
import com.blaie.blaie_be.capture.application.result.CaptureResult;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import com.blaie.blaie_be.core.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/captures")
public class CaptureController {
    private final CaptureService captureService;
    private final ImageCaptureService imageCaptureService;
    private final ImageAssetService imageAssetService;

    public CaptureController(
            CaptureService captureService,
            ImageCaptureService imageCaptureService,
            ImageAssetService imageAssetService
    ) {
        this.captureService = captureService;
        this.imageCaptureService = imageCaptureService;
        this.imageAssetService = imageAssetService;
    }

    @GetMapping("/{captureId}/assets/{assetId}/content")
    public ResponseEntity<Void> assetContent(
            @PathVariable UUID captureId,
            @PathVariable UUID assetId
    ) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(imageAssetService.createOwnedReadUri(captureId, assetId))
                .build();
    }

    @PostMapping(path = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CaptureResponse>> captureImage(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestParam(required = false) String text,
            @RequestParam("image") MultipartFile image
    ) throws IOException {
        CaptureResult result = imageCaptureService.captureImage(
                text,
                new ImageInput(
                        image.getOriginalFilename(),
                        image.getContentType(),
                        image.getBytes()
                ),
                idempotencyKey
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.of(CaptureResponse.from(result)));
    }

    @PostMapping("/text")
    public ResponseEntity<ApiResponse<CaptureResponse>> captureText(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateTextCaptureRequest request
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.of(CaptureResponse.from(
                        captureService.captureText(request.text(), idempotencyKey)
                )));
    }

    @GetMapping("/{captureId}")
    public ApiResponse<CaptureResponse> capture(@PathVariable UUID captureId) {
        return ApiResponse.of(CaptureResponse.from(captureService.capture(captureId)));
    }

    @GetMapping("/resolve")
    public ApiResponse<CaptureResponse> resolve(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return ApiResponse.of(CaptureResponse.from(captureService.resolveCapture(idempotencyKey)));
    }

    @GetMapping
    public ApiResponse<List<CaptureResponse>> captures(
            @RequestParam(defaultValue = "processing") String status,
            @RequestParam(defaultValue = "20") int limit
    ) {
        if (!"processing".equals(status)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "status must be processing");
        }
        return ApiResponse.of(captureService.processingCaptures(limit).stream()
                .map(CaptureResponse::from)
                .toList());
    }

    @PostMapping("/{captureId}/retry")
    public ResponseEntity<ApiResponse<CaptureResponse>> retry(@PathVariable UUID captureId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.of(CaptureResponse.from(captureService.retry(captureId))));
    }

    @DeleteMapping("/{captureId}")
    public ResponseEntity<Void> delete(@PathVariable UUID captureId) {
        captureService.delete(captureId);
        return ResponseEntity.noContent().build();
    }
}
