package com.calorietracker.photo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link S3PhotoStorage} with a mocked {@link S3Client} and {@link S3Presigner} (no AWS calls),
 * plus one check against a real presigner, which signs offline.
 */
class S3PhotoStorageTest {

    private static final String BUCKET = "test-meal-photos";
    private static final String KEY = "meal-photos/2026-10-16/7/abc-full.jpg";

    private S3Client s3;
    private S3Presigner presigner;
    private S3PhotoStorage storage;

    @BeforeEach
    void setUp() {
        s3 = mock(S3Client.class);
        presigner = mock(S3Presigner.class);
        storage = new S3PhotoStorage(s3, presigner, BUCKET);
    }

    @Test
    void put_uploadsTheBytesToTheBucketWithTheContentType() throws Exception {
        byte[] bytes = TestPhotos.jpeg(64);
        storage.put(KEY, bytes, "image/jpeg");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3).putObject(request.capture(), body.capture());
        assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(request.getValue().key()).isEqualTo(KEY);
        assertThat(request.getValue().contentType()).isEqualTo("image/jpeg");
        try (InputStream in = body.getValue().contentStreamProvider().newStream()) {
            assertThat(in.readAllBytes()).isEqualTo(bytes);
        }
    }

    @Test
    void delete_deletesTheKeyFromTheBucket() {
        storage.delete(KEY);

        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3).deleteObject(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(request.getValue().key()).isEqualTo(KEY);
    }

    @Test
    void presignedGetUrl_signsAGetOfTheKeyForTheRequestedDuration() throws Exception {
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://" + BUCKET + ".s3.amazonaws.com/" + KEY + "?X-Amz-Signature=abc"));
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        URI url = storage.presignedGetUrl(KEY, Duration.ofMinutes(10)).orElseThrow();

        assertThat(url).hasToString("https://" + BUCKET + ".s3.amazonaws.com/" + KEY + "?X-Amz-Signature=abc");
        ArgumentCaptor<GetObjectPresignRequest> request = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(presigner).presignGetObject(request.capture());
        assertThat(request.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(10));
        assertThat(request.getValue().getObjectRequest().bucket()).isEqualTo(BUCKET);
        assertThat(request.getValue().getObjectRequest().key()).isEqualTo(KEY);
        verifyNoInteractions(s3);
    }

    @Test
    void read_isNotUsed_photosAreServedByRedirect() {
        assertThat(storage.read(KEY)).isEmpty();
        verifyNoInteractions(s3, presigner);
    }

    @Test
    void realPresigner_producesATenMinuteGetUrlForTheBucketAndKey() {
        // Presigning is local computation: fake credentials, no network.
        try (S3Presigner real = S3Presigner.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access-key", "test-secret-key")))
                .build()) {
            URI url = new S3PhotoStorage(s3, real, BUCKET)
                    .presignedGetUrl(KEY, MealPhotoService.PRESIGNED_URL_TTL).orElseThrow();

            assertThat(url.getScheme()).isEqualTo("https");
            assertThat(url.getHost()).startsWith(BUCKET + ".s3");
            assertThat(url.getPath()).isEqualTo("/" + KEY);
            assertThat(url.getQuery()).contains("X-Amz-Expires=600").contains("X-Amz-Signature=");
        }
    }
}
