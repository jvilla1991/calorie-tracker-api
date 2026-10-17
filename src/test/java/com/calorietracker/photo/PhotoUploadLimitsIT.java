package com.calorietracker.photo;

import com.calorietracker.config.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Upload size and type limits as a real client sees them, on embedded Tomcat: MockMvc does not
 * apply the container's multipart caps ({@code spring.servlet.multipart.*}), and a rejected
 * multipart request must come back as JSON with the right status, not a 500 or a 401/403 from
 * the error forward.
 * <p>
 * Nothing here writes data: no meal has the id used below, and every request is refused before
 * anything is stored.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PhotoUploadLimitsIT {

    private static final String UPLOAD = "/api/diary/meals/999999999/photos";
    private static final int MB = 1024 * 1024;

    private final HttpClient http = HttpClient.newHttpClient();

    @Value("${local.server.port}") int port;
    @Value("${app.admin.username:admin}") String adminUsername;

    @Autowired JwtUtils jwtUtils;

    private HttpResponse<String> upload(TestPhotos.Multipart body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + UPLOAD))
                .header("Authorization", "Bearer " + jwtUtils.generateToken(adminUsername))
                .header("Content-Type", body.contentType())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.build()))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static void assertJsonMessage(HttpResponse<String> res, int status, String message) {
        assertThat(res.statusCode()).isEqualTo(status);
        assertThat(res.headers().firstValue("Content-Type")).hasValueSatisfying(
                ct -> assertThat(ct).startsWith("application/json"));
        assertThat(res.body()).contains("\"message\":\"" + message + "\"");
    }

    @Test
    void partOverTheContainerCap_is413Json() throws Exception {
        // 5 MB is over max-file-size (4 MB): Tomcat rejects it while parsing, before any controller.
        HttpResponse<String> res = upload(new TestPhotos.Multipart()
                .file("full", TestPhotos.jpeg(5 * MB))
                .file("thumb", TestPhotos.jpeg(1000)));
        assertJsonMessage(res, 413, "Upload too large: 'full' must be at most 3 MB and 'thumb' at most 500 KB");
    }

    @Test
    void requestOverTheContainerCap_is413Json() throws Exception {
        // About 8.8 MB in total is over max-request-size (8 MB), with every part under 4 MB.
        HttpResponse<String> res = upload(new TestPhotos.Multipart()
                .file("full", TestPhotos.jpeg(3 * MB + 900 * 1024))
                .file("thumb", TestPhotos.jpeg(3 * MB + 900 * 1024))
                .file("extra", TestPhotos.jpeg(MB)));
        assertJsonMessage(res, 413, "Upload too large: 'full' must be at most 3 MB and 'thumb' at most 500 KB");
    }

    @Test
    void fullOverItsLimitButUnderTheCap_is413Json() throws Exception {
        HttpResponse<String> res = upload(new TestPhotos.Multipart()
                .file("full", TestPhotos.jpeg(3 * MB + 512 * 1024))
                .file("thumb", TestPhotos.jpeg(1000)));
        assertJsonMessage(res, 413, "'full' must be at most 3 MB");
    }

    @Test
    void thumbOverItsLimit_is413Json() throws Exception {
        HttpResponse<String> res = upload(new TestPhotos.Multipart()
                .file("full", TestPhotos.jpeg(1000))
                .file("thumb", TestPhotos.jpeg(600 * 1024)));
        assertJsonMessage(res, 413, "'thumb' must be at most 500 KB");
    }

    @Test
    void notAJpeg_is415Json() throws Exception {
        HttpResponse<String> res = upload(new TestPhotos.Multipart()
                .file("full", TestPhotos.png())
                .file("thumb", TestPhotos.jpeg(1000)));
        assertJsonMessage(res, 415, "'full' must be a JPEG image");
    }

    @Test
    void missingPart_is400Json() throws Exception {
        HttpResponse<String> res = upload(new TestPhotos.Multipart().file("full", TestPhotos.jpeg(1000)));
        assertJsonMessage(res, 400, "Both 'full' and 'thumb' JPEG parts are required");
    }

    @Test
    void multipartWithoutABoundary_is400Json_not500() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + UPLOAD))
                .header("Authorization", "Bearer " + jwtUtils.generateToken(adminUsername))
                .header("Content-Type", "multipart/form-data")
                .POST(HttpRequest.BodyPublishers.ofString("this is not a multipart body"))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertJsonMessage(res, 400, "Malformed multipart request");
    }

    @Test
    void validUploadToAnUnknownMeal_is404Json() throws Exception {
        HttpResponse<String> res = upload(new TestPhotos.Multipart()
                .file("full", TestPhotos.jpeg(2000))
                .file("thumb", TestPhotos.jpeg(300)));
        assertJsonMessage(res, 404, "Meal not found");
    }
}
