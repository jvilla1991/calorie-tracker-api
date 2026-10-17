package com.calorietracker.auth;

import com.calorietracker.config.JwtUtils;
import com.calorietracker.photo.TestPhotos;
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
 * The public read-only view, as a real client sees it (embedded Tomcat, like
 * {@link SecurityStatusCodesIT}, so the {@code /error} forward is exercised too).
 *
 * <ul>
 *   <li>Anonymous GET of the diary day, summary and goals → 200, even with a bad token.</li>
 *   <li>Meal photo GETs are public too: an unknown photo is 404, not 401 (a real photo being
 *       served anonymously is covered by {@code PublicPhotoAccessIT}).</li>
 *   <li>Weight stays private: anonymous GET /api/weigh-ins → 401.</li>
 *   <li>Every write (photo upload/delete and meal details included), and the food/saved-meal
 *       endpoints, still need a token → 401.</li>
 * </ul>
 *
 * Nothing here writes data: anonymous writes are rejected before the controller, and the
 * authenticated writes are built to fail validation (400), which proves they got past security.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PublicReadAccessIT {

    // Far in the past so it cannot touch real diary data.
    private static final String DAY = "1990-09-01";

    private final HttpClient http = HttpClient.newHttpClient();

    @Value("${local.server.port}") int port;
    @Value("${app.admin.username:admin}") String adminUsername;

    @Autowired JwtUtils jwtUtils;

    // --- public GETs -----------------------------------------------------------------------

    @Test
    void anonymousGet_diaryDay_is200() throws Exception {
        HttpResponse<String> res = get("/api/diary/" + DAY, null);
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).contains("\"date\":\"" + DAY + "\"");
    }

    @Test
    void anonymousGet_summary_is200() throws Exception {
        assertThat(get("/api/diary/summary?from=1990-09-01&to=1990-09-03", null).statusCode()).isEqualTo(200);
    }

    @Test
    void anonymousGet_goals_is200() throws Exception {
        assertThat(get("/api/goals", null).statusCode()).isEqualTo(200);
    }

    @Test
    void publicGet_withGarbageOrExpiredToken_isTreatedAsAnonymous_200() throws Exception {
        assertThat(get("/api/diary/" + DAY, "not-a-jwt").statusCode()).isEqualTo(200);
        assertThat(get("/api/diary/summary?from=1990-09-01&to=1990-09-02", "not-a-jwt").statusCode()).isEqualTo(200);
        assertThat(get("/api/goals", "not-a-jwt").statusCode()).isEqualTo(200);
        // Correctly signed, but for a user that does not exist.
        assertThat(get("/api/goals", jwtUtils.generateToken("nobody-by-this-name")).statusCode()).isEqualTo(200);
    }

    @Test
    void anonymousGet_unknownPhoto_is404_not401() throws Exception {
        HttpResponse<String> thumb = get("/api/photos/999999999/thumb", null);
        assertThat(thumb.statusCode()).isEqualTo(404);
        assertThat(thumb.body()).contains("\"message\":\"Photo not found\"");
        assertThat(get("/api/photos/999999999/full", null).statusCode()).isEqualTo(404);
        assertThat(get("/api/photos/999999999/full", "not-a-jwt").statusCode()).isEqualTo(404);
    }

    @Test
    void anonymousPublicGet_badInput_stillAnswers400_not401() throws Exception {
        // The /error forward must not turn a permitted request's 400 into a 401.
        assertThat(get("/api/diary/summary?from=1990-09-05&to=1990-09-01", null).statusCode()).isEqualTo(400);
        assertThat(get("/api/diary/1990-13-45", null).statusCode()).isEqualTo(400);
    }

    // --- still private ---------------------------------------------------------------------

    @Test
    void anonymousGet_weighIns_is401() throws Exception {
        assertThat(get("/api/weigh-ins", null).statusCode()).isEqualTo(401);
        assertThat(get("/api/weigh-ins", "not-a-jwt").statusCode()).isEqualTo(401);
    }

    @Test
    void anonymousGet_foodsAndSavedMeals_are401() throws Exception {
        assertThat(get("/api/foods/recent", null).statusCode()).isEqualTo(401);
        assertThat(get("/api/foods/search?q=apple", null).statusCode()).isEqualTo(401);
        assertThat(get("/api/foods/barcode/123", null).statusCode()).isEqualTo(401);
        assertThat(get("/api/saved-meals", null).statusCode()).isEqualTo(401);
    }

    @Test
    void anonymousGet_otherDiaryPaths_are401() throws Exception {
        // Only a YYYY-MM-DD segment is public; any other single segment is not.
        assertThat(get("/api/diary/meals", null).statusCode()).isEqualTo(401);
        assertThat(get("/api/diary/exercise", null).statusCode()).isEqualTo(401);
    }

    @Test
    void anonymousGet_otherPhotoPaths_are401() throws Exception {
        // Only /api/photos/{digits}/thumb and /full are public.
        assertThat(get("/api/photos/abc/thumb", null).statusCode()).isEqualTo(401);
        assertThat(get("/api/photos/1", null).statusCode()).isEqualTo(401);
        assertThat(get("/api/photos/1/original", null).statusCode()).isEqualTo(401);
        assertThat(get("/api/photos", null).statusCode()).isEqualTo(401);
    }

    @Test
    void anonymousWrites_are401() throws Exception {
        assertThat(send("POST", "/api/diary/" + DAY + "/exercise", "{\"name\":\"Run\",\"calories\":100}", null)
                .statusCode()).isEqualTo(401);
        assertThat(send("DELETE", "/api/diary/exercise/1", null, null).statusCode()).isEqualTo(401);
        assertThat(send("PUT", "/api/diary/" + DAY + "/eating-window",
                "{\"startedAt\":\"1990-09-01T12:00:00Z\"}", null).statusCode()).isEqualTo(401);
        assertThat(send("DELETE", "/api/diary/" + DAY + "/eating-window", null, null).statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/diary/" + DAY + "/meals", "{\"mealType\":\"lunch\"}", null)
                .statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/diary/meals/1/items", "{\"foodId\":1,\"quantityGrams\":10}", null)
                .statusCode()).isEqualTo(401);
        assertThat(send("PUT", "/api/diary/meals/items/1", "{\"quantityGrams\":10}", null).statusCode()).isEqualTo(401);
        assertThat(send("DELETE", "/api/diary/meals/items/1", null, null).statusCode()).isEqualTo(401);
        assertThat(send("PUT", "/api/goals", "{\"calories\":2000}", null).statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/weigh-ins", "{\"date\":\"" + DAY + "\",\"weightKg\":80}", null)
                .statusCode()).isEqualTo(401);
        // A write to a path that is publicly readable is still a write.
        assertThat(send("POST", "/api/diary/" + DAY, "{}", null).statusCode()).isEqualTo(401);
        // Meal details and photos: viewing is public, changing is not.
        assertThat(send("PUT", "/api/diary/meals/1/details", "{\"note\":\"x\",\"estimatedCalories\":100}", null)
                .statusCode()).isEqualTo(401);
        assertThat(uploadPhoto("/api/diary/meals/1/photos", null).statusCode()).isEqualTo(401);
        assertThat(send("DELETE", "/api/photos/1", null, null).statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/photos/1/thumb", "{}", null).statusCode()).isEqualTo(401);
        assertThat(send("DELETE", "/api/photos/1/thumb", null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void writesWithGarbageToken_are401() throws Exception {
        assertThat(send("PUT", "/api/goals", "{\"calories\":2000}", "not-a-jwt").statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/diary/" + DAY + "/exercise", "{\"name\":\"Run\",\"calories\":100}",
                "not-a-jwt").statusCode()).isEqualTo(401);
        assertThat(uploadPhoto("/api/diary/meals/1/photos", "not-a-jwt").statusCode()).isEqualTo(401);
        assertThat(send("DELETE", "/api/photos/1", null, "not-a-jwt").statusCode()).isEqualTo(401);
        assertThat(send("PUT", "/api/diary/meals/1/details", "{\"note\":null,\"estimatedCalories\":null}",
                "not-a-jwt").statusCode()).isEqualTo(401);
    }

    // --- authenticated still works ---------------------------------------------------------

    @Test
    void authenticated_readsStillWork() throws Exception {
        String token = validToken();
        assertThat(get("/api/diary/" + DAY, token).statusCode()).isEqualTo(200);
        assertThat(get("/api/diary/summary?from=1990-09-01&to=1990-09-02", token).statusCode()).isEqualTo(200);
        assertThat(get("/api/goals", token).statusCode()).isEqualTo(200);
        assertThat(get("/api/weigh-ins?from=1990-09-01&to=1990-09-02", token).statusCode()).isEqualTo(200);
        assertThat(get("/api/foods/recent", token).statusCode()).isEqualTo(200);
        assertThat(get("/api/saved-meals", token).statusCode()).isEqualTo(200);
    }

    @Test
    void authenticated_writesPassSecurity_andReachValidation() throws Exception {
        String token = validToken();
        String future = LocalDate.now().plusDays(3).toString();
        // Each body is invalid on purpose: 400 proves security let the request through.
        assertThat(send("POST", "/api/diary/" + DAY + "/exercise", "{\"name\":\"Run\",\"calories\":0}", token)
                .statusCode()).isEqualTo(400);
        assertThat(send("PUT", "/api/diary/" + DAY + "/eating-window", "{}", token).statusCode()).isEqualTo(400);
        assertThat(send("PUT", "/api/goals", "{\"calories\":2000}", token).statusCode()).isEqualTo(400);
        assertThat(send("POST", "/api/diary/" + DAY + "/meals", "{\"mealType\":\"\"}", token)
                .statusCode()).isEqualTo(400);
        assertThat(send("POST", "/api/weigh-ins", "{\"date\":\"" + future + "\",\"weightKg\":80}", token)
                .statusCode()).isEqualTo(400);
        assertThat(send("DELETE", "/api/diary/exercise/999999999", null, token).statusCode()).isEqualTo(404);
        assertThat(send("PUT", "/api/diary/meals/999999999/details", "{\"note\":null,\"estimatedCalories\":0}", token)
                .statusCode()).isEqualTo(400);
        assertThat(send("PUT", "/api/diary/meals/999999999/details", "{\"note\":\"x\",\"estimatedCalories\":1}", token)
                .statusCode()).isEqualTo(404);
        // No meal has this id, so nothing is stored: 404 proves the upload got past security.
        assertThat(uploadPhoto("/api/diary/meals/999999999/photos", token).statusCode()).isEqualTo(404);
        assertThat(send("DELETE", "/api/photos/999999999", null, token).statusCode()).isEqualTo(404);
    }

    // --- helpers ---------------------------------------------------------------------------

    private String validToken() {
        return jwtUtils.generateToken(adminUsername);
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return send("GET", path, null, token);
    }

    /** A well-formed photo upload (small JPEG-looking parts) to {@code path}. */
    private HttpResponse<String> uploadPhoto(String path, String token) throws Exception {
        TestPhotos.Multipart body = new TestPhotos.Multipart()
                .file("full", TestPhotos.jpeg(2000))
                .file("thumb", TestPhotos.jpeg(300));
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", body.contentType())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.build()));
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> send(String method, String path, String json, String token) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, json == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json));
        if (json != null) {
            req.header("Content-Type", "application/json");
        }
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }
}
