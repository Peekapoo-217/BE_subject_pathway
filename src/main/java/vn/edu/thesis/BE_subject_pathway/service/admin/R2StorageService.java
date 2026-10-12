package vn.edu.thesis.BE_subject_pathway.service.admin;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

@Service
public class R2StorageService {
    private final String endpoint;
    private final String bucket;
    private final String region;
    private final String accessKey;
    private final String secretKey;
    private final long urlTtlSeconds;

    public R2StorageService(@Value("${app.storage.r2-endpoint:}") String endpoint,
            @Value("${app.storage.r2-bucket:}") String bucket,
            @Value("${app.storage.r2-region:auto}") String region,
            @Value("${app.storage.r2-access-key-id:}") String accessKey,
            @Value("${app.storage.r2-secret-access-key:}") String secretKey,
            @Value("${app.storage.presigned-url-ttl-seconds:300}") long urlTtlSeconds) {
        this.endpoint = endpoint; this.bucket = bucket; this.region = region;
        this.accessKey = accessKey; this.secretKey = secretKey; this.urlTtlSeconds = urlTtlSeconds;
    }

    public void put(String key, InputStream input, long length, String contentType) {
        try (S3Client s3 = client()) {
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                    RequestBody.fromInputStream(input, length));
        }
    }

    public void delete(String key) {
        try (S3Client s3 = client()) {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        }
    }

    public String presignedGet(String key) {
        requireConfigured();
        try (S3Presigner presigner = S3Presigner.builder().endpointOverride(URI.create(endpoint))
                .region(Region.of(region)).credentialsProvider(credentials())
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build()).build()) {
            var request = GetObjectPresignRequest.builder().signatureDuration(Duration.ofSeconds(urlTtlSeconds))
                    .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build()).build();
            return presigner.presignGetObject(request).url().toString();
        }
    }

    private S3Client client() {
        requireConfigured();
        return S3Client.builder().endpointOverride(URI.create(endpoint)).region(Region.of(region))
                .credentialsProvider(credentials())
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build()).build();
    }

    private StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }

    private void requireConfigured() {
        if (endpoint.isBlank() || bucket.isBlank() || accessKey.isBlank() || secretKey.isBlank())
            throw new IllegalStateException("Cloudflare R2 is not configured; set R2_ENDPOINT, R2_BUCKET, R2_ACCESS_KEY_ID and R2_SECRET_ACCESS_KEY");
    }
}
