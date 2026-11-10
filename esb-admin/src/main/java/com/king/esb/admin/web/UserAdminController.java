package com.king.esb.admin.web;

import com.king.esb.admin.security.UserService;
import com.king.esb.admin.web.dto.UserDtos.CreateUserRequest;
import com.king.esb.admin.web.dto.UserDtos.UserResponse;
import com.king.esb.common.EsbErrorCode;
import com.king.esb.common.EsbException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** User administration (ADMIN only). */
@RestController
@RequestMapping("/admin/api/v1/users")
public class UserAdminController {

    private final UserService userService;

    public UserAdminController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserResponse> list() {
        return userService.list().stream().map(UserResponse::of).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse create(@RequestBody CreateUserRequest request, Authentication authentication) {
        try {
            return UserResponse.of(userService.create(request.username(), request.password(),
                    com.king.esb.admin.web.dto.UserDtos.parseRoles(request.roles()), authentication.getName()));
        } catch (IllegalArgumentException e) {
            throw new EsbException(EsbErrorCode.VALIDATION_ERROR, "Unknown role: " + e.getMessage());
        }
    }
}
