package com.king.esb.admin.persistence.mapper;

import com.king.esb.admin.persistence.entity.RouteRecord;
import com.king.esb.common.EsbConstants;
import com.king.esb.common.EsbException;
import com.king.esb.common.JsonUtil;
import com.king.esb.core.route.ProcessorDefinition;
import com.king.esb.core.route.RouteDefinition;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Maps between the persisted {@link RouteRecord} and the runtime {@link RouteDefinition}, and
 * validates incoming definitions (path, method, pipeline, reserved prefixes).
 */
@Component
public class RouteConfigMapper {

    private static final Set<String> ALLOWED_METHODS =
            Set.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS", "*");

    /** Converts a stored record into a runtime definition using the denormalised identity columns. */
    public RouteDefinition toDefinition(RouteRecord record) {
        RouteDefinition stored = JsonUtil.fromJson(record.getConfigJson(), RouteDefinition.class);
        return new RouteDefinition(
                record.getId(),
                record.getName(),
                record.getDescription(),
                record.getPath(),
                record.getMethod(),
                record.getPriority(),
                record.isEnabled(),
                record.getApiKey(),
                stored.rateLimit(),
                stored.pipeline(),
                stored.errorHandler());
    }

    /** Applies a validated definition onto a (new or existing) record. */
    public void apply(RouteDefinition definition, RouteRecord record) {
        validate(definition);
        record.setId(definition.id());
        record.setName(definition.name());
        record.setDescription(definition.description());
        record.setPath(definition.path());
        record.setMethod(definition.method());
        record.setPriority(definition.priority());
        record.setEnabled(definition.enabled());
        record.setApiKey(definition.apiKey());
        record.setConfigJson(JsonUtil.toJson(definition));
    }

    /** Validates a route definition, throwing {@link EsbException#validation} on the first problem set. */
    public void validate(RouteDefinition definition) {
        List<EsbException.FieldError> errors = new ArrayList<>();
        if (isBlank(definition.name())) {
            errors.add(new EsbException.FieldError("name", "name is required"));
        }
        if (isBlank(definition.path())) {
            errors.add(new EsbException.FieldError("path", "path is required"));
        } else if (!definition.path().startsWith("/")) {
            errors.add(new EsbException.FieldError("path", "path must start with '/'"));
        } else if (isReserved(definition.path())) {
            errors.add(new EsbException.FieldError("path",
                    "path must not use a reserved prefix: " + EsbConstants.RESERVED_PREFIXES));
        }
        String method = definition.method();
        if (method != null && !ALLOWED_METHODS.contains(method.toUpperCase(Locale.ROOT))) {
            errors.add(new EsbException.FieldError("method", "unsupported method: " + method));
        }
        errors.addAll(validatePipeline(definition.pipeline()));
        if (!errors.isEmpty()) {
            throw EsbException.validation("Route definition is invalid", errors);
        }
    }

    private List<EsbException.FieldError> validatePipeline(List<ProcessorDefinition> pipeline) {
        List<EsbException.FieldError> errors = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int index = 0;
        for (ProcessorDefinition processor : pipeline) {
            if (processor.type() == null) {
                errors.add(new EsbException.FieldError("pipeline[" + index + "].type", "processor type is required"));
            }
            if (processor.id() == null || processor.id().isBlank()) {
                errors.add(new EsbException.FieldError("pipeline[" + index + "].id", "processor id is required"));
            } else if (!ids.add(processor.id())) {
                errors.add(new EsbException.FieldError("pipeline[" + index + "].id",
                        "duplicate processor id: " + processor.id()));
            }
            index++;
        }
        return errors;
    }

    /** Records a create/update timestamp and actor. */
    public void touch(RouteRecord record, String actor, boolean creating) {
        Instant now = Instant.now();
        if (creating) {
            record.setCreatedAt(now);
            record.setCreatedBy(actor);
            record.setVersion(1);
        }
        record.setUpdatedAt(now);
        record.setUpdatedBy(actor);
    }

    private boolean isReserved(String path) {
        return EsbConstants.RESERVED_PREFIXES.stream()
                .anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
