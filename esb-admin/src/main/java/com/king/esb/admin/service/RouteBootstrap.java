package com.king.esb.admin.service;

import com.king.esb.admin.persistence.mapper.RouteConfigMapper;
import com.king.esb.admin.persistence.repository.RouteRepository;
import com.king.esb.core.registry.RouteRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Loads all enabled routes from the database into the runtime {@link RouteRegistry} on startup,
 * so the gateway is ready without an explicit publish call.
 */
@Component
public class RouteBootstrap {

    private static final Logger log = LoggerFactory.getLogger(RouteBootstrap.class);

    private final RouteRepository repository;
    private final RouteConfigMapper mapper;
    private final RouteRegistry registry;

    public RouteBootstrap(RouteRepository repository, RouteConfigMapper mapper, RouteRegistry registry) {
        this.repository = repository;
        this.mapper = mapper;
        this.registry = registry;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadRoutes() {
        var definitions = repository.findByEnabledTrue().stream()
                .map(mapper::toDefinition)
                .toList();
        registry.replaceAll(definitions);
        log.info("Loaded {} enabled route(s) into the runtime registry", definitions.size());
    }
}
