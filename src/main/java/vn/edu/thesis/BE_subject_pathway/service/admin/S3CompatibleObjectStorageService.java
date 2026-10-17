package vn.edu.thesis.BE_subject_pathway.service.admin;

import java.io.InputStream;
import java.time.Duration;
import java.util.Locale;

import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import vn.edu.thesis.BE_subject_pathway.config.ObjectStorageProperties;

@Service
public class S3CompatibleObjectStorageService implements ObjectStorageService {
    private final S3Client client;
    private final S3Presigner presigner;
    private final ObjectStorageProperties properties;

    public S3CompatibleObjectStorageService(S3Client objectStorageS3Client, S3Presigner objectStorageS3Presigner,
            ObjectStorageProperties properties) {
        this.client = objectStorageS3Client;
        this.presigner = objectStorageS3Presigner;
        this.properties = properties;
    }

    @Override
    public void put(String key, InputStream input, long length, String contentType) {
        requireConfigured();
        client.putObject(PutObjectRequest.builder().bucket(properties.bucket()).key(key)
                .contentType(contentType).build(), RequestBody.fromInputStream(input, length));
    }

    @Override
    public void delete(String key) {
        requireConfigured();
        client.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build());
    }

    @Override
    public boolean exists(String key) {
        requireConfigured();
        try {
            client.headObject(HeadObjectRequest.builder().bucket(properties.bucket()).key(key).build());
            return true;
        } catch (S3Exception exception) {
            String errorCode = exception.awsErrorDetails() == null ? ""
                    : exception.awsErrorDetails().errorCode();
            if (exception.statusCode() == 404 || "NoSuchKey".equals(errorCode)
                    || "NotFound".equals(errorCode) || "404".equals(errorCode)) {
                return false;
            }
            throw exception;
        }
    }

    @Override
    public String presignedGet(String key) {
        requireConfigured();
        var request = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(properties.presignedUrlTtlSeconds()))
                .getObjectRequest(GetObjectRequest.builder().bucket(properties.bucket()).key(key).build())
                .build();
        return presigner.presignGetObject(request).url().toString();
    }

    private void requireConfigured() {
        String provider = properties.provider() == null ? "" : properties.provider().trim().toUpperCase(Locale.ROOT);
        if (!provider.equals("MINIO") && !provider.equals("R2")) {
            throw new IllegalStateException("OBJECT_STORAGE_PROVIDER must be MINIO or R2");
        }
        if (isBlank(properties.endpoint()) || isBlank(properties.publicEndpoint())
                || isBlank(properties.bucket()) || isBlank(properties.region())
                || isBlank(properties.accessKeyId()) || isBlank(properties.secretAccessKey())
                || properties.presignedUrlTtlSeconds() < 1) {
            throw new IllegalStateException("Object storage configuration is incomplete");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
