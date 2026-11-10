package com.king.esb.admin.persistence.repository;

import com.king.esb.admin.persistence.entity.RouteRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Spring Data repository for route definitions. */
public interface RouteRepository extends JpaRepository<RouteRecord, String> {

    Optional<RouteRecord> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, String id);

    List<RouteRecord> findByEnabledTrue();

    Page<RouteRecord> findByEnabled(boolean enabled, Pageable pageable);
}
