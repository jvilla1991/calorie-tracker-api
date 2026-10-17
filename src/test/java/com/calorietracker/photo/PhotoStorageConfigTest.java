package com.calorietracker.photo;

import com.calorietracker.diary.DiaryMealRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Which photo storage {@link PhotoStorageConfig} creates for each combination of bucket property
 * and profile. Builds real S3 clients, which needs no network or credentials until first use.
 */
class PhotoStorageConfigTest {

    @TempDir Path dir;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PhotoStorageConfig.class);

    private ApplicationContextRunner withProfile(String profile) {
        return runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles(profile));
    }

    @Test
    void bucketSet_usesS3_inTheConfiguredRegion() {
        withProfile("prod")
                .withPropertyValues("app.media.bucket= villa-meal-photos ", "app.media.region=us-west-2")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(PhotoStorage.class);
                    assertThat(ctx.getBean(PhotoStorage.class)).isInstanceOf(S3PhotoStorage.class);
                    assertThat(((S3PhotoStorage) ctx.getBean(PhotoStorage.class)).bucket())
                            .isEqualTo("villa-meal-photos");
                    assertThat(ctx.getBean(S3Client.class).serviceClientConfiguration().region())
                            .isEqualTo(Region.US_WEST_2);
                    assertThat(ctx).hasSingleBean(S3Presigner.class);
                });
    }

    @Test
    void bucketSet_regionDefaultsToUsEast1() {
        runner.withPropertyValues("app.media.bucket=villa-meal-photos")
                .run(ctx -> assertThat(ctx.getBean(S3Client.class).serviceClientConfiguration().region())
                        .isEqualTo(Region.US_EAST_1));
        runner.withPropertyValues("app.media.bucket=villa-meal-photos", "app.media.region=")
                .run(ctx -> assertThat(ctx.getBean(S3Client.class).serviceClientConfiguration().region())
                        .isEqualTo(Region.US_EAST_1));
    }

    @Test
    void noBucket_outsideProd_usesLocalFilesInTheConfiguredDirectory() {
        withProfile("local")
                .withPropertyValues("app.media.local-dir=" + dir)
                .run(ctx -> {
                    assertThat(ctx.getBean(PhotoStorage.class)).isInstanceOf(LocalPhotoStorage.class);
                    assertThat(((LocalPhotoStorage) ctx.getBean(PhotoStorage.class)).root())
                            .isEqualTo(dir.toAbsolutePath().normalize());
                    assertThat(ctx).doesNotHaveBean(S3Client.class);
                    assertThat(ctx).doesNotHaveBean(S3Presigner.class);
                });
    }

    @Test
    void noBucket_localDirDefaultsToBuildLocalMedia() {
        runner.run(ctx -> assertThat(((LocalPhotoStorage) ctx.getBean(PhotoStorage.class)).root())
                .isEqualTo(Path.of("build/local-media").toAbsolutePath().normalize()));
    }

    @Test
    void noBucketInProd_thePhotoServiceStillStarts_andAnswers503() {
        withProfile("prod")
                .withBean(MealPhotoRepository.class, () -> mock(MealPhotoRepository.class))
                .withBean(DiaryMealRepository.class, () -> mock(DiaryMealRepository.class))
                .withBean(MealPhotoService.class)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    MealPhotoService service = ctx.getBean(MealPhotoService.class);
                    assertThatThrownBy(() -> service.delete(1L))
                            .isInstanceOf(ResponseStatusException.class)
                            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
    }

    @Test
    void noOrBlankBucket_inProd_createsNoStorageAndNoS3Clients() {
        for (String bucket : new String[]{null, "", "   "}) {
            ApplicationContextRunner prod = withProfile("prod");
            if (bucket != null) {
                prod = prod.withPropertyValues("app.media.bucket=" + bucket);
            }
            prod.run(ctx -> {
                assertThat(ctx).hasNotFailed();
                assertThat(ctx).doesNotHaveBean(PhotoStorage.class);
                assertThat(ctx).doesNotHaveBean(S3Client.class);
                assertThat(ctx).doesNotHaveBean(S3Presigner.class);
            });
        }
    }
}
