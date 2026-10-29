package com.learnium.storage;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * S3-compatible {@link StorageService} (Cloudflare R2 in production). Keys
 * have the same shape as the local implementation's, so switching providers
 * by configuration never changes what the rest of the code sees.
 */
@Service
@ConditionalOnProperty(name = "learnium.storage.type", havingValue = "s3")
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final String bucket;

    public S3StorageService(S3Client s3Client, StorageProperties properties) {
        this.s3Client = s3Client;
        this.bucket = properties.getS3().getBucket();
    }

    @Override
    public String store(UUID centreId, UUID materialId, String filename, byte[] content) {
        String key = centreId + "/" + materialId + "/" + LocalStorageService.sanitize(filename);
        try {
            s3Client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key).build(),
                    RequestBody.fromBytes(content));
        } catch (S3Exception e) {
            throw new StorageException("Failed to store object " + key, e);
        }
        return key;
    }

    @Override
    public byte[] read(String key) {
        try {
            ResponseBytes<GetObjectResponse> response =
                    s3Client.getObjectAsBytes(
                            GetObjectRequest.builder().bucket(bucket).key(key).build());
            return response.asByteArray();
        } catch (S3Exception e) {
            throw new StorageException("Failed to read object " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (S3Exception e) {
            throw new StorageException("Failed to delete object " + key, e);
        }
    }
}
