package com.blaie.blaie_be.capture.infrastructure.storage;

import com.blaie.blaie_be.capture.application.port.ObjectStoragePort;
import com.blaie.blaie_be.capture.application.port.StoredObject;
import com.blaie.blaie_be.capture.application.port.StoredObjectPage;
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
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Component
public class R2ObjectStorageAdapter implements ObjectStoragePort {
    private final R2Properties properties;
    private volatile S3Client s3;
    private volatile S3Presigner presigner;

    @Autowired
    public R2ObjectStorageAdapter(R2Properties properties) {
        this.properties = properties;
    }

    R2ObjectStorageAdapter(
            R2Properties properties,
            S3Client s3,
            S3Presigner presigner
    ) {
        this.properties = properties;
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
            throw unavailable(exception);
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
            throw unavailable(exception);
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
            throw unavailable(exception);
        } catch (Exception exception) {
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
            throw unavailable(exception);
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
            throw unavailable(exception);
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
}
