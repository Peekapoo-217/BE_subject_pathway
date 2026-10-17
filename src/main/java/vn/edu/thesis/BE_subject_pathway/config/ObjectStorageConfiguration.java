package vn.edu.thesis.BE_subject_pathway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(ObjectStorageProperties.class)
public class ObjectStorageConfiguration {
    @Bean(destroyMethod = "close")
    S3Client objectStorageS3Client(ObjectStorageProperties properties) {
        return S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner objectStorageS3Presigner(ObjectStorageProperties properties) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(properties.publicEndpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    private AwsCredentialsProvider credentials(ObjectStorageProperties properties) {
        return () -> {
            if (isBlank(properties.accessKeyId()) || isBlank(properties.secretAccessKey())) {
                throw new IllegalStateException(
                        "OBJECT_STORAGE_ACCESS_KEY_ID and OBJECT_STORAGE_SECRET_ACCESS_KEY are required");
            }
            return AwsBasicCredentials.create(properties.accessKeyId(), properties.secretAccessKey());
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
