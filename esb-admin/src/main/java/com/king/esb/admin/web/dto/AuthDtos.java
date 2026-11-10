package com.king.esb.admin.web.dto;

import java.util.List;

/** Login request/response DTOs. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(String username, String password) {
    }

    public record LoginResponse(String token, List<String> roles, long expiresIn) {
    }

    public record CurrentUser(String username, List<String> roles) {
    }
}
