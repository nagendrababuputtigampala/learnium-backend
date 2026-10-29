package com.learnium.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

class S3StorageServiceTest {

    private static final UUID CENTRE = UUID.randomUUID();
    private static final UUID MATERIAL = UUID.randomUUID();

    private final S3Client s3Client = mock(S3Client.class);

    private S3StorageService service() {
        StorageProperties properties = new StorageProperties();
        properties.getS3().setBucket("learnium-files");
        return new S3StorageService(s3Client, properties);
    }

    @Test
    void storePutsTheObjectUnderTheIdKey() {
        byte[] content = "bytes".getBytes(StandardCharsets.UTF_8);

        String key = service().store(CENTRE, MATERIAL, "my chapter.pdf", content);

        assertThat(key).isEqualTo(CENTRE + "/" + MATERIAL + "/my_chapter.pdf");
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().bucket()).isEqualTo("learnium-files");
        assertThat(captor.getValue().key()).isEqualTo(key);
    }

    @Test
    void storeWrapsSdkFailures() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("boom").build());

        assertThatExceptionOfType(StorageException.class)
                .isThrownBy(() -> service().store(CENTRE, MATERIAL, "a.pdf", new byte[] {1}));
    }

    @Test
    void readReturnsTheObjectBytes() {
        byte[] content = "read me".getBytes(StandardCharsets.UTF_8);
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(
                        ResponseBytes.fromByteArray(
                                GetObjectResponse.builder().build(), content));

        assertThat(service().read("some/key.pdf")).isEqualTo(content);
    }

    @Test
    void readWrapsSdkFailures() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("boom").build());

        assertThatExceptionOfType(StorageException.class)
                .isThrownBy(() -> service().read("some/key.pdf"));
    }

    @Test
    void deleteRemovesTheObject() {
        service().delete("some/key.pdf");

        ArgumentCaptor<DeleteObjectRequest> captor =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("learnium-files");
        assertThat(captor.getValue().key()).isEqualTo("some/key.pdf");
    }

    @Test
    void deleteWrapsSdkFailures() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("boom").build());

        assertThatExceptionOfType(StorageException.class)
                .isThrownBy(() -> service().delete("some/key.pdf"));
    }

    @Test
    void storageConfigBuildsAClientFromProperties() {
        StorageProperties properties = new StorageProperties();
        properties.getS3().setEndpoint("http://localhost:9999");
        properties.getS3().setRegion("us-east-1");
        properties.getS3().setAccessKey("test-access");
        properties.getS3().setSecretKey("test-secret");

        try (S3Client client = new StorageConfig().s3Client(properties)) {
            assertThat(client).isNotNull();
        }
    }

    @Test
    void storagePropertiesDefaultsAreLocalAndR2Shaped() {
        StorageProperties properties = new StorageProperties();
        assertThat(properties.getType()).isEqualTo("local");
        assertThat(properties.getLocalDir()).isEqualTo("./data/files");
        assertThat(properties.getS3().getRegion()).isEqualTo("us-east-1");
        assertThat(properties.getS3().getBucket()).isEmpty();
    }
}
