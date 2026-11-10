package com.king.esb.admin.persistence.repository;

import com.king.esb.admin.persistence.entity.CallLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

/** Spring Data repository for call logs with a filtered query. */
public interface CallLogRepository extends JpaRepository<CallLogEntity, Long> {

    @Query("""
            select c from CallLogEntity c
            where (:routeId is null or c.routeId = :routeId)
              and (:status is null or c.status = :status)
              and (:from is null or c.createdAt >= :from)
              and (:to is null or c.createdAt <= :to)
            order by c.id desc
            """)
    Page<CallLogEntity> search(@Param("routeId") String routeId,
                               @Param("status") String status,
                               @Param("from") Instant from,
                               @Param("to") Instant to,
                               Pageable pageable);

    long countByStatusNot(String status);

    @Query("select coalesce(avg(c.durationMs), 0) from CallLogEntity c")
    double averageDuration();

    @Query("select count(c) from CallLogEntity c")
    long totalCount();
}
