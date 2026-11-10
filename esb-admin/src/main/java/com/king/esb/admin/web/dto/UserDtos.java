package com.king.esb.admin.web.dto;

import com.king.esb.admin.persistence.entity.UserRecord;
import com.king.esb.admin.security.Role;

import java.util.Arrays;
import java.util.List;

/** User request/response DTOs. */
public final class UserDtos {

    private UserDtos() {
    }

    public record CreateUserRequest(String username, String password, List<String> roles) {
    }

    public record UserResponse(String id, String username, List<String> roles, boolean enabled) {

        public static UserResponse of(UserRecord record) {
            List<String> roles = record.getRoles() == null ? List.of()
                    : Arrays.stream(record.getRoles().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
            return new UserResponse(record.getId(), record.getUsername(), roles, record.isEnabled());
        }
    }

    public static List<Role> parseRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of(Role.VIEWER);
        }
        return roles.stream().map(r -> Role.valueOf(r.trim().toUpperCase())).toList();
    }
}
