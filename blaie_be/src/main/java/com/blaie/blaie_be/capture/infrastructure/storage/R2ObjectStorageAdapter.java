package com.blaie.blaie_be.capture.infrastructure.storage;

import com.blaie.blaie_be.capture.application.port.ObjectStoragePort;
import com.blaie.blaie_be.capture.application.port.StoredObject;
import com.blaie.blaie_be.capture.application.port.StoredObjectPage;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort.StorageOperation;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Component
public class R2ObjectStorageAdapter implements ObjectStoragePort {
    private final R2Properties properties;
    private final CaptureTelemetryPort telemetry;
    private volatile S3Client s3;
    private volatile S3Presigner presigner;

    @Autowired
    public R2ObjectStorageAdapter(
            R2Properties properties,
            CaptureTelemetryPort telemetry
    ) {
        this.properties = properties;
        this.telemetry = telemetry;
    }

    R2ObjectStorageAdapter(R2Properties properties) {
        this.properties = properties;
        this.telemetry = null;
    }

    R2ObjectStorageAdapter(
            R2Properties properties,
            S3Client s3,
            S3Presigner presigner
    ) {
        this(properties, s3, presigner, null);
    }

    R2ObjectStorageAdapter(
            R2Properties properties,
            S3Client s3,
            S3Presigner presigner,
            CaptureTelemetryPort telemetry
    ) {
        this.properties = properties;
        this.telemetry = telemetry;
        this.s3 = s3;
        this.presigner = presigner;
    }

    @Override
    public void put(String objectKey, byte[] bytes, String contentType) {
        try {
            client().putObject(
                    PutObjectRequest.builder()
                            .bucket(requireBucket())
                            .key(objectKey)
                            .contentType(contentType)
                            .contentLength((long) bytes.length)
                            .build(),
                    RequestBody.fromBytes(bytes)
            );
        } catch (SdkException exception) {
            recordError(StorageOperation.UPLOAD);
            throw unavailable(exception);
        } catch (AppException exception) {
            recordError(StorageOperation.UPLOAD);
            throw exception;
        }
    }

    @Override
    public byte[] get(String objectKey) {
        try {
            return client().getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(requireBucket())
                    .key(objectKey)
                    .build()).asByteArray();
        } catch (SdkException exception) {
            recordError(StorageOperation.DOWNLOAD);
            throw unavailable(exception);
        } catch (AppException exception) {
            recordError(StorageOperation.DOWNLOAD);
            throw exception;
        }
    }

    @Override
    public URI createReadUri(String objectKey, Duration ttl) {
        try {
            return signer().presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(request -> request
                            .bucket(requireBucket())
                            .key(objectKey))
                    .build()).url().toURI();
        } catch (SdkException exception) {
            recordError(StorageOperation.SIGN_READ);
            throw unavailable(exception);
        } catch (AppException exception) {
            recordError(StorageOperation.SIGN_READ);
            throw exception;
        } catch (Exception exception) {
            recordError(StorageOperation.SIGN_READ);
            throw new AppException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client().deleteObject(DeleteObjectRequest.builder()
                    .bucket(requireBucket())
                    .key(objectKey)
                    .build());
        } catch (SdkException exception) {
            recordError(StorageOperation.DELETE);
            throw unavailable(exception);
        } catch (AppException exception) {
            recordError(StorageOperation.DELETE);
            throw exception;
        }
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            client().headObject(HeadObjectRequest.builder()
                    .bucket(requireBucket())
                    .key(objectKey)
                    .build());
            return true;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            recordError(StorageOperation.HEAD);
            throw unavailable(exception);
        } catch (SdkException exception) {
            recordError(StorageOperation.HEAD);
            throw unavailable(exception);
        } catch (AppException exception) {
            recordError(StorageOperation.HEAD);
            throw exception;
        }
    }

    @Override
    public StoredObjectPage list(String prefix, String continuationToken, int limit) {
        try {
            var request = ListObjectsV2Request.builder()
                    .bucket(requireBucket())
                    .prefix(prefix)
                    .continuationToken(continuationToken)
                    .maxKeys(limit)
                    .build();
            var response = client().listObjectsV2(request);
            return new StoredObjectPage(
                    response.contents().stream()
                            .map(object -> new StoredObject(object.key(), object.lastModified()))
                            .toList(),
                    Boolean.TRUE.equals(response.isTruncated())
                            ? response.nextContinuationToken()
                            : null
            );
        } catch (SdkException exception) {
            recordError(StorageOperation.LIST);
            throw unavailable(exception);
        } catch (AppException exception) {
            recordError(StorageOperation.LIST);
            throw exception;
        }
    }

    private String requireBucket() {
        if (properties.bucket() == null || properties.bucket().isBlank()) {
            throw new AppException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
        }
        return properties.bucket();
    }

    private S3Client client() {
        if (s3 == null) {
            synchronized (this) {
                if (s3 == null) {
                    requireConfigured();
                    s3 = S3Client.builder()
                            .endpointOverride(properties.endpoint())
                            .region(Region.of(properties.region()))
                            .credentialsProvider(credentials())
                            .serviceConfiguration(S3Configuration.builder()
                                    .pathStyleAccessEnabled(true)
                                    .build())
                            .build();
                }
            }
        }
        return s3;
    }

    private S3Presigner signer() {
        if (presigner == null) {
            synchronized (this) {
                if (presigner == null) {
                    requireConfigured();
                    presigner = S3Presigner.builder()
                            .endpointOverride(properties.endpoint())
                            .region(Region.of(properties.region()))
                            .credentialsProvider(credentials())
                            .serviceConfiguration(S3Configuration.builder()
                                    .pathStyleAccessEnabled(true)
                                    .build())
                            .build();
                }
            }
        }
        return presigner;
    }

    private StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                properties.accessKeyId(),
                properties.secretAccessKey()
        ));
    }

    private void requireConfigured() {
        if (!properties.configured()) {
            throw new AppException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
        }
    }

    private AppException unavailable(SdkException exception) {
        return new AppException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
    }

    private void recordError(StorageOperation operation) {
        if (telemetry != null) {
            telemetry.incrementStorageError(operation);
        }
    }
}
