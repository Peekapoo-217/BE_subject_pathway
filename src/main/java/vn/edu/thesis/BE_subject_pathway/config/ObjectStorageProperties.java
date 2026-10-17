package vn.edu.thesis.BE_subject_pathway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record ObjectStorageProperties(
        String provider,
        String endpoint,
        String publicEndpoint,
        String bucket,
        String region,
        String accessKeyId,
        String secretAccessKey,
        long presignedUrlTtlSeconds) {
}
