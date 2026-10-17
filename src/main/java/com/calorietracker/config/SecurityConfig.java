package com.calorietracker.config;

import com.calorietracker.user.UserRepository;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Paths anyone may GET without a token (read-only public view). A request to one of these
     * that carries a bad or expired token is treated as anonymous and still succeeds:
     * {@link JwtFilter} never rejects, it only skips authentication.
     */
    static final String[] PUBLIC_GET_PATHS = {
            "/api/diary/summary",
            "/api/diary/{date:\\d{4}-\\d{2}-\\d{2}}",
            "/api/goals",
            // Meal photos, so the read-only diary can show them in a plain <img> (no auth header).
            "/api/photos/{id:\\d+}/thumb",
            "/api/photos/{id:\\d+}/full",
    };

    @Value("${cors.allowed-origins:http://localhost:5173}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtFilter jwtFilter) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Let Spring Boot's error forward (/error) through. The original request was
                // already authorized (or rejected) on the way in; without this the forward
                // itself is re-checked as an anonymous request and every 400/404/500 a
                // controller produces is masked as a 403/401.
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/auth/login").permitAll()
                // Public read-only view: anyone may GET the diary day view, the summary, the
                // current goals and the meal photos without logging in. GET only, so every write
                // (POST/PUT/DELETE) on these or any other path still needs a token: uploading or
                // deleting a photo and editing a meal's details stay private. {date} is pinned to
                // the YYYY-MM-DD shape so no other single-segment /api/diary/* path is opened, and
                // a photo {id} to digits.
                // Deliberately NOT public: /api/weigh-ins/** (weight stays private, GET too),
                // /api/foods/** (search calls an external API; recent foods only serve the
                // logging UI) and /api/saved-meals/**.
                .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS).permitAll()
                .anyRequest().authenticated()
            )
            // No/invalid/expired token -> 401. Without an entry point, this stateless setup
            // falls back to Http403ForbiddenEntryPoint and answers 403 instead. Authenticated
            // but forbidden requests still get 403 from the default AccessDeniedHandler.
            // This also covers a failed login: AuthenticationManager's BadCredentialsException
            // reaches ExceptionTranslationFilter and is answered with 401.
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository) {
        return username -> userRepository.findByUsername(username)
                .map(u -> User.withUsername(u.getUsername())
                        .password(u.getPassword())
                        .authorities("ROLE_USER")
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
