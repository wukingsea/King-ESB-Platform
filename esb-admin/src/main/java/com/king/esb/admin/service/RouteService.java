package com.king.esb.admin.service;

import com.king.esb.admin.persistence.entity.RouteRecord;
import com.king.esb.admin.persistence.mapper.RouteConfigMapper;
import com.king.esb.admin.persistence.repository.RouteRepository;
import com.king.esb.common.EsbErrorCode;
import com.king.esb.common.EsbException;
import com.king.esb.core.route.RouteDefinition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** CRUD operations for route definitions, including unique-name enforcement and status toggling. */
@Service
public class RouteService {

    private final RouteRepository repository;
    private final RouteConfigMapper mapper;
    private final AuditService auditService;
    private final RoutePublishService publishService;

    public RouteService(RouteRepository repository, RouteConfigMapper mapper, AuditService auditService,
                        RoutePublishService publishService) {
        this.repository = repository;
        this.mapper = mapper;
        this.auditService = auditService;
        this.publishService = publishService;
    }

    @Transactional(readOnly = true)
    public Page<RouteRecord> list(Boolean enabled, Pageable pageable) {
        return enabled == null ? repository.findAll(pageable) : repository.findByEnabled(enabled, pageable);
    }

    @Transactional(readOnly = true)
    public RouteRecord get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new EsbException(EsbErrorCode.NOT_FOUND, "Route not found: " + id));
    }

    @Transactional(readOnly = true)
    public RouteDefinition getDefinition(String id) {
        return mapper.toDefinition(get(id));
    }

    @Transactional
    public RouteRecord create(RouteDefinition definition, String actor) {
        String id = definition.id() == null || definition.id().isBlank()
                ? UUID.randomUUID().toString()
                : definition.id();
        if (repository.existsByName(definition.name())) {
            throw new EsbException(EsbErrorCode.ROUTE_NAME_EXISTS, "Route name already exists: " + definition.name());
        }
        RouteDefinition withId = copyWithId(definition, id);
        RouteRecord record = new RouteRecord();
        mapper.apply(withId, record);
        mapper.touch(record, actor, true);
        RouteRecord saved = repository.save(record);
        auditService.record(actor, "ROUTE_CREATE", id, saved.getName());
        return saved;
    }

    @Transactional
    public RouteRecord update(String id, RouteDefinition definition, String actor) {
        RouteRecord record = get(id);
        if (definition.name() != null && repository.existsByNameAndIdNot(definition.name(), id)) {
            throw new EsbException(EsbErrorCode.ROUTE_NAME_EXISTS, "Route name already exists: " + definition.name());
        }
        RouteDefinition withId = copyWithId(definition, id);
        mapper.apply(withId, record);
        record.setVersion(record.getVersion() + 1);
        mapper.touch(record, actor, false);
        RouteRecord saved = repository.save(record);
        auditService.record(actor, "ROUTE_UPDATE", id, saved.getName());
        return saved;
    }

    @Transactional
    public RouteRecord setEnabled(String id, boolean enabled, String actor) {
        RouteRecord record = get(id);
        record.setEnabled(enabled);
        mapper.touch(record, actor, false);
        RouteRecord saved = repository.save(record);
        if (enabled) {
            publishService.publish(id, actor);
        } else {
            publishService.unpublish(id);
        }
        auditService.record(actor, enabled ? "ROUTE_ENABLE" : "ROUTE_DISABLE", id, saved.getName());
        return saved;
    }

    @Transactional
    public void delete(String id, String actor) {
        RouteRecord record = get(id);
        repository.delete(record);
        auditService.record(actor, "ROUTE_DELETE", id, record.getName());
    }

    private RouteDefinition copyWithId(RouteDefinition definition, String id) {
        return new RouteDefinition(id, definition.name(), definition.description(), definition.path(),
                definition.method(), definition.priority(), definition.enabled(), definition.apiKey(),
                definition.rateLimit(), definition.pipeline(), definition.errorHandler());
    }
}
