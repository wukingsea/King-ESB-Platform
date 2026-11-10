package com.king.esb.admin.web;

import com.king.esb.admin.persistence.entity.UserRecord;
import com.king.esb.admin.security.JwtService;
import com.king.esb.admin.security.Role;
import com.king.esb.admin.security.UserService;
import com.king.esb.admin.web.dto.AuthDtos.CurrentUser;
import com.king.esb.admin.web.dto.AuthDtos.LoginRequest;
import com.king.esb.admin.web.dto.AuthDtos.LoginResponse;
import com.king.esb.common.EsbErrorCode;
import com.king.esb.common.EsbException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Authentication endpoints: login and current-user introspection. */
@RestController
@RequestMapping("/admin/api/v1/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserService userService, JwtService jwtService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        UserRecord user = request.username() == null ? null : userService.findByUsername(request.username());
        if (user == null || !user.isEnabled()
                || !passwordEncoder.matches(request.password() == null ? "" : request.password(),
                        user.getPasswordHash())) {
            throw new EsbException(EsbErrorCode.INVALID_CREDENTIALS, "Invalid username or password");
        }
        List<Role> roles = userService.rolesOf(user);
        String token = jwtService.generate(user.getUsername(), roles);
        return new LoginResponse(token, roles.stream().map(Enum::name).toList(), jwtService.ttlSeconds());
    }

    @GetMapping("/me")
    public CurrentUser me(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                .toList();
        return new CurrentUser(authentication.getName(), roles);
    }
}
