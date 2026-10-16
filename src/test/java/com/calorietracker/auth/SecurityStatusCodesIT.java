package com.calorietracker.auth;

import com.calorietracker.config.JwtUtils;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Status codes as a real client sees them. Runs on a real embedded Tomcat (not MockMvc) because
 * one of the bugs this guards against lives in the servlet container's error forward to
 * {@code /error}, which MockMvc never performs.
 *
 * <ul>
 *   <li>Missing / garbage / expired JWT → 401 (not 403).</li>
 *   <li>Authenticated request with a bad body → 400 (not 403).</li>
 *   <li>Wrong login password → 401; right password → 200.</li>
 * </ul>
 *
 * None of these requests write data: every write attempted here fails validation first.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityStatusCodesIT {

    private final HttpClient http = HttpClient.newHttpClient();

    @Value("${local.server.port}") int port;
    @Value("${app.admin.username:admin}") String adminUsername;
    @Value("${app.admin.password}") String adminPassword;
    @Value("${app.jwt.secret}") String jwtSecret;

    @Autowired JwtUtils jwtUtils;

    // --- unauthenticated -------------------------------------------------------------------

    @Test
    void noToken_is401() throws Exception {
        assertThat(get("/api/weigh-ins", null).statusCode()).isEqualTo(401);
        assertThat(get("/api/goals", null).statusCode()).isEqualTo(401);
    }

    @Test
    void garbageToken_is401() throws Exception {
        assertThat(get("/api/goals", "not-a-jwt").statusCode()).isEqualTo(401);
    }

    @Test
    void tokenSignedWithAnotherKey_is401() throws Exception {
        String forged = Jwts.builder()
                .subject(adminUsername)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key("some-other-secret-that-is-also-at-least-32-bytes"))
                .compact();
        assertThat(get("/api/goals", forged).statusCode()).isEqualTo(401);
    }

    @Test
    void expiredToken_is401() throws Exception {
        // Same key derivation as JwtUtils, but already expired.
        String expired = Jwts.builder()
                .subject(adminUsername)
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(key(jwtSecret))
                .compact();
        assertThat(get("/api/goals", expired).statusCode()).isEqualTo(401);
    }

    @Test
    void validTokenForUnknownUser_is401_not500() throws Exception {
        String token = jwtUtils.generateToken("nobody-by-this-name");
        assertThat(get("/api/goals", token).statusCode()).isEqualTo(401);
    }

    @Test
    void removedRegisterEndpoint_unauthenticated_is401() throws Exception {
        // /api/auth/register no longer exists, so it is just another protected path.
        HttpResponse<String> res = post("/api/auth/register",
                "{\"username\":\"stranger\",\"password\":\"secret\"}", null);
        assertThat(res.statusCode()).isEqualTo(401);
    }

    @Test
    void actuatorHealth_isPublic() throws Exception {
        assertThat(get("/actuator/health", null).statusCode()).isEqualTo(200);
    }

    // --- authenticated, bad input → 400 ----------------------------------------------------

    @Test
    void validToken_futureWeighInDate_is400() throws Exception {
        String body = "{\"date\":\"" + LocalDate.now().plusDays(3) + "\",\"weightKg\":80}";
        assertThat(post("/api/weigh-ins", body, validToken()).statusCode()).isEqualTo(400);
    }

    @Test
    void validToken_goalsMissingFields_is400() throws Exception {
        assertThat(put("/api/goals", "{\"calories\":2000}", validToken()).statusCode()).isEqualTo(400);
    }

    @Test
    void validToken_responseStatusExceptionBadRequest_is400() throws Exception {
        // WeighInService throws ResponseStatusException(BAD_REQUEST) when from > to.
        HttpResponse<String> res = get("/api/weigh-ins?from=2020-02-01&to=2020-01-01", validToken());
        assertThat(res.statusCode()).isEqualTo(400);
    }

    @Test
    void validToken_unmappedPath_is404() throws Exception {
        assertThat(get("/api/does-not-exist", validToken()).statusCode()).isEqualTo(404);
    }

    @Test
    void validToken_goodRequest_is200() throws Exception {
        assertThat(get("/api/goals", validToken()).statusCode()).isEqualTo(200);
    }

    // --- login -----------------------------------------------------------------------------

    @Test
    void login_wrongPassword_is401() throws Exception {
        String body = "{\"username\":\"" + adminUsername + "\",\"password\":\"definitely-wrong\"}";
        assertThat(post("/api/auth/login", body, null).statusCode()).isEqualTo(401);
    }

    @Test
    void login_unknownUser_is401() throws Exception {
        String body = "{\"username\":\"nobody-by-this-name\",\"password\":\"whatever\"}";
        assertThat(post("/api/auth/login", body, null).statusCode()).isEqualTo(401);
    }

    @Test
    void login_correctPassword_is200WithToken() throws Exception {
        HttpResponse<String> res = post("/api/auth/login", loginBody(), null);
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).contains("\"token\"");
    }

    // --- helpers ---------------------------------------------------------------------------

    private String validToken() {
        return jwtUtils.generateToken(adminUsername);
    }

    private String loginBody() {
        return "{\"username\":\"" + adminUsername + "\",\"password\":\"" + adminPassword + "\"}";
    }

    /** Mirrors JwtUtils' key derivation (UTF-8 bytes, zero-padded to at least 32). */
    private static SecretKeySpec key(String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(Arrays.copyOf(bytes, Math.max(bytes.length, 32)), "HmacSHA256");
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return send(base(path).GET(), token);
    }

    private HttpResponse<String> post(String path, String json, String token) throws Exception {
        return send(base(path).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)), token);
    }

    private HttpResponse<String> put(String path, String json, String token) throws Exception {
        return send(base(path).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json)), token);
    }

    private HttpRequest.Builder base(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    private HttpResponse<String> send(HttpRequest.Builder req, String token) throws Exception {
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }
}
