package com.blaie.blaie_be.capture.infrastructure.storage;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "blaie.storage.r2")
public class R2Properties {
    private URI endpoint;
    private String region = "auto";
    private String bucket;
    private String accessKeyId;
    private String secretAccessKey;

    public URI endpoint() {
        return endpoint;
    }

    public String region() {
        return region;
    }

    public String bucket() {
        return bucket;
    }

    public String accessKeyId() {
        return accessKeyId;
    }

    public String secretAccessKey() {
        return secretAccessKey;
    }

    public void setEndpoint(URI endpoint) {
        this.endpoint = endpoint;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public void setSecretAccessKey(String secretAccessKey) {
        this.secretAccessKey = secretAccessKey;
    }

    boolean configured() {
        return endpoint != null
                && hasText(bucket)
                && hasText(accessKeyId)
                && hasText(secretAccessKey);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
