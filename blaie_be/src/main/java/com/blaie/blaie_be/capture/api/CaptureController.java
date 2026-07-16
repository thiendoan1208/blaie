package com.blaie.blaie_be.capture.api;

import com.blaie.blaie_be.capture.api.request.CreateTextCaptureRequest;
import com.blaie.blaie_be.capture.api.response.CaptureResponse;
import com.blaie.blaie_be.capture.application.CaptureService;
import com.blaie.blaie_be.core.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/captures")
public class CaptureController {
    private final CaptureService captureService;

    public CaptureController(CaptureService captureService) {
        this.captureService = captureService;
    }

    @PostMapping("/text")
    public ResponseEntity<ApiResponse<CaptureResponse>> captureText(
            @Valid @RequestBody CreateTextCaptureRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(CaptureResponse.from(captureService.captureText(request.text()))));
    }
}
