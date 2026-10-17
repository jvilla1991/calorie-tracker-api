package com.calorietracker.auth;

import com.calorietracker.config.JwtUtils;
import com.calorietracker.diary.DiaryEntryRepository;
import com.calorietracker.diary.DiaryMealRepository;
import com.calorietracker.photo.MealPhoto;
import com.calorietracker.photo.MealPhotoRepository;
import com.calorietracker.photo.PhotoStorage;
import com.calorietracker.photo.TestPhotos;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Meal photos in the public read-only view, end to end on embedded Tomcat (like
 * {@link PublicReadAccessIT}): a photo the logged-in owner uploads can be fetched by anyone, as
 * the dietician's browser does in a plain {@code <img>}, while uploading, deleting and editing a
 * meal's details stay private. The context uses local photo storage (local profile).
 * <p>
 * Unlike {@code PublicReadAccessIT} this writes data: the server commits real rows and files, so
 * each test works on a far-past date and {@link #removeEverythingCreated} deletes what it made.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PublicPhotoAccessIT {

    private static final String DAY = "1990-09-20";

    private final HttpClient http = HttpClient.newHttpClient();

    @Value("${local.server.port}") int port;
    @Value("${app.admin.username:admin}") String adminUsername;

    @Autowired JwtUtils jwtUtils;
    @Autowired DiaryEntryRepository entryRepo;
    @Autowired DiaryMealRepository mealRepo;
    @Autowired MealPhotoRepository photoRepo;
    @Autowired PhotoStorage storage;

    private boolean entryExistedBefore;
    private long mealId;

    @BeforeEach
    void createMeal() throws Exception {
        entryExistedBefore = entryRepo.findByEntryDate(LocalDate.parse(DAY)).isPresent();
        HttpResponse<String> res = send("POST", "/api/diary/" + DAY + "/meals", "{\"mealType\":\"dinner\"}", token());
        assertThat(res.statusCode()).isEqualTo(201);
        mealId = id(res);
    }

    @AfterEach
    void removeEverythingCreated() {
        for (MealPhoto p : photoRepo.findByDiaryMealIdOrderByIdAsc(mealId)) {
            storage.delete(p.getFullKey());
            storage.delete(p.getThumbKey());
        }
        mealRepo.deleteById(mealId); // its photo rows go with it (ON DELETE CASCADE)
        if (!entryExistedBefore) {
            entryRepo.findByEntryDate(LocalDate.parse(DAY)).ifPresent(entryRepo::delete);
        }
    }

    @Test
    void anyoneCanViewAnUploadedPhoto_onlyTheOwnerCanDeleteIt() throws Exception {
        byte[] full = TestPhotos.jpeg(4000);
        byte[] thumb = TestPhotos.jpeg(500);
        HttpResponse<String> uploaded = upload(full, thumb, token());
        assertThat(uploaded.statusCode()).isEqualTo(201);
        long photoId = id(uploaded);
        String thumbUrl = JsonPath.read(uploaded.body(), "$.thumbUrl");
        String fullUrl = JsonPath.read(uploaded.body(), "$.fullUrl");

        // Anonymous, exactly as an <img src> would fetch it: no Authorization header.
        HttpResponse<byte[]> t = getBytes(thumbUrl, null);
        assertThat(t.statusCode()).isEqualTo(200);
        assertThat(t.headers().firstValue("Content-Type")).hasValue("image/jpeg");
        assertThat(t.headers().firstValue("Cache-Control")).hasValue("private, max-age=300");
        assertThat(t.body()).isEqualTo(thumb);
        assertThat(getBytes(fullUrl, null).body()).isEqualTo(full);
        // A bad token on a public path is treated as anonymous.
        assertThat(getBytes(fullUrl, "not-a-jwt").statusCode()).isEqualTo(200);

        // The public diary lists the photo on its meal.
        HttpResponse<String> day = send("GET", "/api/diary/" + DAY, null, null);
        assertThat(day.statusCode()).isEqualTo(200);
        assertThat(day.body()).contains("\"thumbUrl\":\"" + thumbUrl + "\"");

        // Viewing is public; changing is not.
        assertThat(send("DELETE", "/api/photos/" + photoId, null, null).statusCode()).isEqualTo(401);
        assertThat(send("DELETE", "/api/photos/" + photoId, null, "not-a-jwt").statusCode()).isEqualTo(401);
        assertThat(upload(full, thumb, null).statusCode()).isEqualTo(401);
        assertThat(getBytes(thumbUrl, null).statusCode()).isEqualTo(200);
        assertThat(photoRepo.countByDiaryMealId(mealId)).isEqualTo(1);

        // The owner deletes it; then it is gone for everyone.
        assertThat(send("DELETE", "/api/photos/" + photoId, null, token()).statusCode()).isEqualTo(204);
        assertThat(getBytes(thumbUrl, null).statusCode()).isEqualTo(404);
        assertThat(getBytes(fullUrl, null).statusCode()).isEqualTo(404);
    }

    @Test
    void mealDetails_onlyTheOwnerCanEdit_anyoneCanSeeThem() throws Exception {
        String body = "{\"note\":\"Dinner out\",\"estimatedCalories\":900}";
        assertThat(send("PUT", "/api/diary/meals/" + mealId + "/details", body, null).statusCode()).isEqualTo(401);

        HttpResponse<String> saved = send("PUT", "/api/diary/meals/" + mealId + "/details", body, token());
        assertThat(saved.statusCode()).isEqualTo(200);

        HttpResponse<String> day = send("GET", "/api/diary/" + DAY, null, null);
        assertThat(day.statusCode()).isEqualTo(200);
        assertThat(day.body()).contains("\"note\":\"Dinner out\"").contains("\"estimatedCalories\":900");
    }

    // --- helpers ---------------------------------------------------------------------------

    private String token() {
        return jwtUtils.generateToken(adminUsername);
    }

    private static long id(HttpResponse<String> res) {
        return ((Number) JsonPath.read(res.body(), "$.id")).longValue();
    }

    private HttpResponse<String> upload(byte[] full, byte[] thumb, String token) throws Exception {
        TestPhotos.Multipart body = new TestPhotos.Multipart()
                .file("full", full)
                .file("thumb", thumb)
                .field("width", "1600")
                .field("height", "1200");
        HttpRequest.Builder req = request("/api/diary/meals/" + mealId + "/photos", token)
                .header("Content-Type", body.contentType())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.build()));
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<byte[]> getBytes(String path, String token) throws Exception {
        return http.send(request(path, token).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<String> send(String method, String path, String json, String token) throws Exception {
        HttpRequest.Builder req = request(path, token).method(method, json == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json));
        if (json != null) {
            req.header("Content-Type", "application/json");
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder request(String path, String token) {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return req;
    }
}
