package com.king.esb.app.demo;

import com.king.esb.admin.persistence.entity.RouteRecord;
import com.king.esb.admin.persistence.repository.RouteRepository;
import com.king.esb.admin.service.RouteService;
import com.king.esb.core.route.EndpointDefinition;
import com.king.esb.core.route.EndpointType;
import com.king.esb.core.route.ProcessorDefinition;
import com.king.esb.core.route.ProcessorType;
import com.king.esb.core.route.RouteDefinition;
import com.king.esb.core.route.TargetDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Seeds a demo route in the {@code dev} profile: {@code /demo/echo/**} is mediated (logged, tagged
 * with a header, then proxied) to the platform-internal echo endpoint, so the platform is
 * immediately demonstrable after startup. The route is picked up by the registry on
 * {@code ApplicationReadyEvent} once its record is committed.
 */
@Component
@Profile("dev")
@Order(100)
public class DemoRouteSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoRouteSeeder.class);
    private static final String ROUTE_NAME = "demo-echo";
    private static final String SYSTEM_ACTOR = "system";

    private final RouteService routeService;
    private final RouteRepository routeRepository;
    private final ServletWebServerApplicationContext webContext;

    public DemoRouteSeeder(RouteService routeService, RouteRepository routeRepository,
                           ServletWebServerApplicationContext webContext) {
        this.routeService = routeService;
        this.routeRepository = routeRepository;
        this.webContext = webContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (routeRepository.existsByName(ROUTE_NAME)) {
            log.info("Demo route '{}' already present; skipping seed", ROUTE_NAME);
            return;
        }
        String uri = echoUri();
        RouteRecord saved = routeService.create(buildDefinition(uri), SYSTEM_ACTOR);
        log.info("Seeded demo route '{}' ({}) -> {}", ROUTE_NAME, saved.getId(), uri);
    }

    private String echoUri() {
        int port = webContext.getWebServer() == null ? 8080 : webContext.getWebServer().getPort();
        return "http://127.0.0.1:" + port + "/internal/echo";
    }

    private RouteDefinition buildDefinition(String uri) {
        EndpointDefinition endpoint =
                new EndpointDefinition("echo-1", uri, EndpointType.HTTP, "POST", Map.of(), 0, 0, 0, null);
        TargetDefinition target = new TargetDefinition("echo", List.of(endpoint), null, null, true);
        List<ProcessorDefinition> pipeline = List.of(
                new ProcessorDefinition("log-request", ProcessorType.LOG,
                        Map.<String, Object>of("level", "info",
                                "message", "demo echo request ${method} ${path}")),
                new ProcessorDefinition("tag-demo", ProcessorType.SET_HEADER,
                        Map.<String, Object>of("name", "X-ESB-Demo", "value", "king-esb")),
                new ProcessorDefinition("call-echo", ProcessorType.SERVICE_CALL,
                        Map.<String, Object>of("target", target)));
        return new RouteDefinition(
                null,
                ROUTE_NAME,
                "Dev demo route: mediates /demo/echo/** to the platform-internal echo endpoint",
                "/demo/echo/**",
                RouteDefinition.ANY_METHOD,
                100,
                true,
                null,
                null,
                pipeline,
                null);
    }
}
