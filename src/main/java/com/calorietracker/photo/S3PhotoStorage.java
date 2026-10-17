package com.calorietracker.photo;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Optional;

/**
 * Photos in a private S3 bucket (prod). Browsers never get bucket access: the API answers a photo
 * request with a redirect to a short-lived presigned GET URL. Errors from S3 propagate as the
 * SDK's runtime exceptions.
 */
public class S3PhotoStorage implements PhotoStorage {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    public S3PhotoStorage(S3Client s3, S3Presigner presigner, String bucket) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = bucket;
    }

    public String bucket() {
        return bucket;
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(bytes));
    }

    /** S3 answers a delete of a missing key with success, so "not found" needs no special case. */
    @Override
    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    /** Signs locally with the current credentials; no call to S3 is made. */
    @Override
    public Optional<URI> presignedGetUrl(String key, Duration ttl) {
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .build();
        try {
            return Optional.of(presigner.presignGetObject(request).url().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("S3 returned an invalid presigned URL for " + key, e);
        }
    }

    /** Not used for S3: photos are served by redirecting to {@link #presignedGetUrl}. */
    @Override
    public Optional<byte[]> read(String key) {
        return Optional.empty();
    }
}
