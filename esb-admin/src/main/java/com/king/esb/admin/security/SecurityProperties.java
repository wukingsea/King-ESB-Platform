package com.king.esb.admin.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Security configuration bound from {@code esb.security.*}. Secrets must be supplied via environment
 * variables in production; the dev profile provides non-secret defaults.
 */
@ConfigurationProperties(prefix = "esb.security")
public class SecurityProperties {

    /** HS256 signing secret; must be at least 32 bytes. */
    private String jwtSecret = "";

    /** Token lifetime in minutes. */
    private long tokenTtlMinutes = 480;

    /** Bootstrap admin username. */
    private String adminUsername = "admin";

    /** Bootstrap admin password (required unless a user already exists). */
    private String adminPassword = "";

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public long getTokenTtlMinutes() {
        return tokenTtlMinutes;
    }

    public void setTokenTtlMinutes(long tokenTtlMinutes) {
        this.tokenTtlMinutes = tokenTtlMinutes;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public void setAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }
}
