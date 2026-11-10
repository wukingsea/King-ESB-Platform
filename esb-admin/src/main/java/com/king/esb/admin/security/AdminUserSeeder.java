package com.king.esb.admin.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Creates the bootstrap admin user from {@code esb.security.admin-username/-password} if it does
 * not already exist. The password is never hard-coded; it comes from configuration/environment.
 */
@Component
public class AdminUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserSeeder.class);

    private final UserService userService;
    private final SecurityProperties properties;

    public AdminUserSeeder(UserService userService, SecurityProperties properties) {
        this.userService = userService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String username = properties.getAdminUsername();
        String password = properties.getAdminPassword();
        if (password == null || password.isBlank()) {
            log.warn("esb.security.admin-password is not set; skipping bootstrap admin user creation");
            return;
        }
        userService.ensureUser(username, password, List.of(Role.ADMIN, Role.OPERATOR, Role.VIEWER));
        log.info("Bootstrap admin user '{}' ensured", username);
    }
}
