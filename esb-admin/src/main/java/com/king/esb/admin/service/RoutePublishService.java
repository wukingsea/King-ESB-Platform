package com.king.esb.admin.service;

import com.king.esb.admin.persistence.entity.RouteRecord;
import com.king.esb.admin.persistence.mapper.RouteConfigMapper;
import com.king.esb.admin.persistence.repository.RouteRepository;
import com.king.esb.common.EsbErrorCode;
import com.king.esb.common.EsbException;
import com.king.esb.core.registry.RouteRegistry;
import com.king.esb.core.route.RouteDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes persisted routes into the live {@link RouteRegistry} (hot reload) and removes them on
 * disable/delete. Publication compiles the stored definition and performs an atomic registry swap.
 */
@Service
public class RoutePublishService {

    private final RouteRepository repository;
    private final RouteConfigMapper mapper;
    private final RouteRegistry registry;
    private final AuditService auditService;

    public RoutePublishService(RouteRepository repository, RouteConfigMapper mapper,
                               RouteRegistry registry, AuditService auditService) {
        this.repository = repository;
        this.mapper = mapper;
        this.registry = registry;
        this.auditService = auditService;
    }

    /** Compiles a route into the registry, bumping its version. Disabled routes are not activated. */
    @Transactional
    public RouteRecord publish(String id, String actor) {
        RouteRecord record = repository.findById(id)
                .orElseThrow(() -> new EsbException(EsbErrorCode.NOT_FOUND, "Route not found: " + id));
        RouteDefinition definition = mapper.toDefinition(record);
        if (definition.enabled()) {
            registry.publish(definition);
        } else {
            registry.remove(id);
        }
        record.setVersion(record.getVersion() + 1);
        mapper.touch(record, actor, false);
        RouteRecord saved = repository.save(record);
        auditService.record(actor, "ROUTE_PUBLISH", id, definition.name());
        return saved;
    }

    /** Removes a route from the live registry. */
    public void unpublish(String id) {
        registry.remove(id);
    }
}
