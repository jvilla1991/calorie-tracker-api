package com.calorietracker.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the auth surface: login stays public, registration no longer exists.
 * Runs against the full Spring context (needs the local Postgres, like contextLoads).
 */
@SpringBootTest
class AuthEndpointsSecurityTest {

    @Autowired WebApplicationContext context;

    @Value("${app.admin.username:admin}") String adminUsername;
    @Value("${app.admin.password}") String adminPassword;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void register_isNotPubliclyReachable() throws Exception {
        // The endpoint was removed, so this is an unauthenticated request to a protected path: 401.
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"stranger\",\"password\":\"secret\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_wrongPassword_is401() throws Exception {
        String body = "{\"username\":\"" + adminUsername + "\",\"password\":\"definitely-wrong\"}";
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_stillWorksForSeededAdmin() throws Exception {
        String body = "{\"username\":\"" + adminUsername + "\",\"password\":\"" + adminPassword + "\"}";
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }
}
