package com.blaie.blaie_be.capture.infrastructure.storage;

import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort.StorageOperation;
import com.blaie.blaie_be.core.error.AppException;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class R2ObjectStorageAdapterTest {
    @Test
    void usesPrivateBucketAndOnlyReturnsShortLivedPresignedReadUrls() throws Exception {
        R2Properties properties = new R2Properties();
        properties.setBucket("private-images");
        S3Client s3 = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        R2ObjectStorageAdapter adapter = new R2ObjectStorageAdapter(properties, s3, presigner);
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(
                        GetObjectResponse.builder().build(),
                        new byte[]{4, 5, 6}
                ));
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://signed.example/object?signature=opaque").toURL());
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        adapter.put("captures/asset.png", new byte[]{1, 2, 3}, "image/png");
        assertThat(adapter.get("captures/asset.png")).containsExactly(4, 5, 6);
        URI readUri = adapter.createReadUri("captures/asset.png", Duration.ofMinutes(2));
        adapter.delete("captures/asset.png");

        ArgumentCaptor<PutObjectRequest> put = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(put.capture(), any(RequestBody.class));
        assertThat(put.getValue().bucket()).isEqualTo("private-images");
        assertThat(put.getValue().key()).isEqualTo("captures/asset.png");
        assertThat(put.getValue().contentType()).isEqualTo("image/png");
        ArgumentCaptor<GetObjectPresignRequest> signed =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(presigner).presignGetObject(signed.capture());
        assertThat(signed.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(2));
        assertThat(readUri).hasToString("https://signed.example/object?signature=opaque");
    }

    @Test
    void distinguishesMissingObjectsFromStorageFailuresAndRecordsBoundedTelemetry() {
        R2Properties properties = new R2Properties();
        properties.setBucket("private-images");
        S3Client s3 = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        CaptureTelemetryPort telemetry = mock(CaptureTelemetryPort.class);
        R2ObjectStorageAdapter adapter =
                new R2ObjectStorageAdapter(properties, s3, presigner, telemetry);

        when(s3.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build())
                .thenThrow(S3Exception.builder().statusCode(404).message("not found").build())
                .thenThrow(S3Exception.builder().statusCode(503).message("unavailable").build());

        assertThat(adapter.exists("captures/present.png")).isTrue();
        assertThat(adapter.exists("captures/missing.png")).isFalse();
        assertThatThrownBy(() -> adapter.exists("captures/unknown.png"))
                .isInstanceOf(AppException.class);

        verify(telemetry).incrementStorageError(StorageOperation.HEAD);
        verify(telemetry, never()).incrementStorageError(StorageOperation.DOWNLOAD);
    }
}
