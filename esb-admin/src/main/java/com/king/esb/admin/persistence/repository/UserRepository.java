package com.king.esb.admin.persistence.repository;

import com.king.esb.admin.persistence.entity.UserRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Spring Data repository for platform users. */
public interface UserRepository extends JpaRepository<UserRecord, String> {

    Optional<UserRecord> findByUsername(String username);

    boolean existsByUsername(String username);
}
