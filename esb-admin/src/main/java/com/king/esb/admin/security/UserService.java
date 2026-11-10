package com.king.esb.admin.security;

import com.king.esb.admin.persistence.entity.UserRecord;
import com.king.esb.admin.persistence.repository.UserRepository;
import com.king.esb.admin.service.AuditService;
import com.king.esb.common.EsbErrorCode;
import com.king.esb.common.EsbException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** User management: creation with BCrypt hashing and lookup used by authentication. */
@Service
public class UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder, AuditService auditService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<UserRecord> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public UserRecord findByUsername(String username) {
        return repository.findByUsername(username).orElse(null);
    }

    @Transactional
    public UserRecord create(String username, String rawPassword, List<Role> roles, String actor) {
        if (username == null || username.isBlank()) {
            throw new EsbException(EsbErrorCode.VALIDATION_ERROR, "username is required");
        }
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new EsbException(EsbErrorCode.VALIDATION_ERROR, "password must be at least 8 characters");
        }
        if (repository.existsByUsername(username)) {
            throw new EsbException(EsbErrorCode.CONFLICT, "User already exists: " + username);
        }
        UserRecord user = new UserRecord();
        user.setId(UUID.randomUUID().toString());
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRoles(joinRoles(roles));
        user.setEnabled(true);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        UserRecord saved = repository.save(user);
        auditService.record(actor, "USER_CREATE", username, saved.getRoles());
        return saved;
    }

    /** Ensures a bootstrap user exists; used by {@link AdminUserSeeder}. */
    @Transactional
    public void ensureUser(String username, String rawPassword, List<Role> roles) {
        if (repository.existsByUsername(username)) {
            return;
        }
        create(username, rawPassword, roles, "system");
    }

    public List<Role> rolesOf(UserRecord user) {
        if (user.getRoles() == null || user.getRoles().isBlank()) {
            return List.of();
        }
        return Arrays.stream(user.getRoles().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Role::valueOf)
                .toList();
    }

    private String joinRoles(List<Role> roles) {
        return (roles == null || roles.isEmpty() ? List.of(Role.VIEWER) : roles).stream()
                .map(Enum::name)
                .reduce((a, b) -> a + "," + b)
                .orElse(Role.VIEWER.name());
    }
}
