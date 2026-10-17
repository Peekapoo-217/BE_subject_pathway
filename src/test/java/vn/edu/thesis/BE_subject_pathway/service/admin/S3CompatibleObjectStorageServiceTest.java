package vn.edu.thesis.BE_subject_pathway.service.admin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import vn.edu.thesis.BE_subject_pathway.config.ObjectStorageProperties;

class S3CompatibleObjectStorageServiceTest {
    private final S3Client client = mock(S3Client.class);
    private final S3Presigner presigner = mock(S3Presigner.class);

    @Test
    void reportsObjectMissingForAnS3NotFoundResponse() {
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("missing").statusCode(404).build());
        var service = service("MINIO");

        assertFalse(service.exists("missing.pdf"));
    }

    @Test
    void reportsObjectPresentWhenHeadRequestSucceeds() {
        var service = service("R2");

        assertTrue(service.exists("existing.pdf"));
    }

    @Test
    void rejectsUnknownProviderBeforeMakingStorageRequest() {
        var service = service("OTHER");

        assertThrows(IllegalStateException.class, () -> service.exists("file.pdf"));
    }

    private S3CompatibleObjectStorageService service(String provider) {
        var properties = new ObjectStorageProperties(provider, "http://minio:9000", "http://localhost:9000",
                "documents", "us-east-1", "access-key", "secret-key", 300);
        return new S3CompatibleObjectStorageService(client, presigner, properties);
    }
}
