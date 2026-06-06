package com.calorietracker.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class UserSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    public UserSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Upsert the admin user, keeping its password in sync with the configured value.
        // Previously the admin was seeded only once and never updated, so rotating
        // APP_ADMIN_PASSWORD silently had no effect (the DB kept the original hash). Now we
        // re-hash and save whenever the configured password doesn't match the stored hash.
        User admin = userRepository.findByUsername(adminUsername)
                .orElseGet(() -> {
                    User u = new User();
                    u.setUsername(adminUsername);
                    return u;
                });

        boolean isNew = admin.getId() == null;
        if (isNew || !passwordEncoder.matches(adminPassword, admin.getPassword())) {
            admin.setPassword(passwordEncoder.encode(adminPassword));
            userRepository.save(admin);
        }
    }
}
