package com.calorietracker.photo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.nio.file.Path;

/**
 * Chooses where meal photos are stored, from the {@code app.media.*} properties:
 * <ul>
 *   <li><b>{@code app.media.bucket} set</b> (prod: env {@code MEDIA_BUCKET}):
 *       {@link S3PhotoStorage} in that private bucket. The region is {@code app.media.region}
 *       (prod: env {@code MEDIA_REGION}, default {@code us-east-1}). Credentials come from the AWS
 *       default provider chain, i.e. the App Runner instance role. The S3 client beans exist only
 *       in this case, so local runs and tests never need AWS.</li>
 *   <li><b>No bucket, any profile except {@code prod}</b>: {@link LocalPhotoStorage} under
 *       {@code app.media.local-dir} (default {@code build/local-media}, relative to the working
 *       directory).</li>
 *   <li><b>No bucket in {@code prod}</b>: no storage at all, on purpose, so a release never breaks
 *       the API when the infrastructure lags behind. The API starts and works normally;
 *       {@link MealPhotoService} logs a WARN and the photo endpoints answer
 *       503 {@code {"message": "Photo storage not configured"}}.</li>
 * </ul>
 */
@Configuration
public class PhotoStorageConfig {

    private static final Logger log = LoggerFactory.getLogger(PhotoStorageConfig.class);

    static final String DEFAULT_REGION = "us-east-1";

    @Bean
    @Conditional(BucketConfigured.class)
    S3Client mediaS3Client(@Value("${app.media.region:" + DEFAULT_REGION + "}") String region) {
        return S3Client.builder()
                .region(region(region))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    @Bean
    @Conditional(BucketConfigured.class)
    S3Presigner mediaS3Presigner(@Value("${app.media.region:" + DEFAULT_REGION + "}") String region) {
        return S3Presigner.builder().region(region(region)).build();
    }

    @Bean
    @Conditional(BucketConfigured.class)
    PhotoStorage s3PhotoStorage(S3Client s3, S3Presigner presigner,
                                @Value("${app.media.bucket}") String bucket) {
        log.info("Meal photos are stored in S3 bucket {}", bucket.trim());
        return new S3PhotoStorage(s3, presigner, bucket.trim());
    }

    @Bean
    @Conditional(BucketNotConfigured.class)
    @Profile("!prod")
    PhotoStorage localPhotoStorage(@Value("${app.media.local-dir:build/local-media}") String dir) {
        LocalPhotoStorage storage = new LocalPhotoStorage(Path.of(dir));
        log.info("Meal photos are stored on the local filesystem under {} (set app.media.bucket to use S3)",
                storage.root());
        return storage;
    }

    /** The configured region, falling back to {@value #DEFAULT_REGION} when blank. */
    static Region region(String configured) {
        return Region.of(StringUtils.hasText(configured) ? configured.trim() : DEFAULT_REGION);
    }

    /** Matches when {@code app.media.bucket} has a non-blank value. */
    static class BucketConfigured implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(context.getEnvironment().getProperty("app.media.bucket"));
        }
    }

    /** Matches when {@code app.media.bucket} is missing or blank. */
    static class BucketNotConfigured implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return !new BucketConfigured().matches(context, metadata);
        }
    }
}
