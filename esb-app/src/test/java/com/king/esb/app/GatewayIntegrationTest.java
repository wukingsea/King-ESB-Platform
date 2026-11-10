package com.king.esb.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end admin + gateway integration test: boot the full application on H2 with no Redis,
 * exercise the admin API (auth, route CRUD, publish) and drive the gateway against a stub backend.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability(metrics = true, tracing = false)
@ActiveProfiles("test")
class GatewayIntegrationTest {

    private static HttpServer backend;
    private static int backendPort;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    static void startBackend() throws IOException {
        backend = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        backend.createContext("/stub", exchange -> {
            byte[] body = "pong".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        backend.createContext("/fail", exchange -> {
            byte[] body = "broken".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        backend.start();
        backendPort = backend.getAddress().getPort();
    }

    @AfterAll
    static void stopBackend() {
        backend.stop(0);
    }

    // ---------------------------------------------------------------- helpers

    private String login() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of("username", "admin", "password", "admin1234"));
        String response = mockMvc.perform(post("/admin/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        String token = node.path("token").asText();
        assertNotNull(token);
        assertTrue(token.length() > 10);
        return token;
    }

    private Map<String, Object> endpoint(String uri) {
        Map<String, Object> endpoint = new LinkedHashMap<>();
        endpoint.put("id", "e1");
        endpoint.put("uri", uri);
        endpoint.put("type", "HTTP");
        endpoint.put("method", "POST");
        return endpoint;
    }

    private Map<String, Object> serviceCallProcessor(String uri) {
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("id", "stub");
        target.put("endpoints", List.of(endpoint(uri)));
        target.put("strategy", "ROUND_ROBIN");
        target.put("isDefault", true);

        Map<String, Object> processor = new LinkedHashMap<>();
        processor.put("id", "call");
        processor.put("type", "SERVICE_CALL");
        processor.put("config", Map.of("target", target));
        return processor;
    }

    private Map<String, Object> route(String name, String path, String uri, String apiKey,
                                      Integer rateLimit) {
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("name", name);
        definition.put("description", "integration test route");
        definition.put("path", path);
        definition.put("method", "*");
        definition.put("priority", 100);
        definition.put("enabled", true);
        if (apiKey != null) {
            definition.put("apiKey", apiKey);
        }
        if (rateLimit != null) {
            definition.put("rateLimit", Map.of("requestsPerWindow", 1, "windowSeconds", 60, "keyBy", "ip"));
        }
        Map<String, Object> processor = serviceCallProcessor(uri);
        definition.put("pipeline", List.of(processor));
        return definition;
    }

    private String createAndPublish(String token, Map<String, Object> definition) throws Exception {
        String created = mockMvc.perform(post("/admin/api/v1/routes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(definition)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(created).path("id").asText();

        mockMvc.perform(post("/admin/api/v1/routes/" + id + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        return id;
    }

    // ---------------------------------------------------------------- tests

    @Test
    void adminEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/admin/api/v1/routes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void loginReturnsTokenAndCurrentUserReflectsRoles() throws Exception {
        String token = login();
        mockMvc.perform(get("/admin/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.roles").isArray());
    }

    @Test
    void gatewayRoutesToStubBackendAndReturnsTraceHeader() throws Exception {
        String token = login();
        createAndPublish(token, route("gw-passthrough", "/gw/echo", stubUri("/stub"), null, null));

        mockMvc.perform(post("/gw/echo").contentType(MediaType.APPLICATION_JSON).content("{\"hi\":1}"))
                .andExpect(status().isOk())
                .andExpect(result -> assertNotNull(result.getResponse().getHeader("X-ESB-Trace-Id")))
                .andExpect(result -> assertTrue(
                        result.getResponse().getContentAsString().contains("pong")));
    }

    @Test
    void unmatchedGatewayPathReturns404() throws Exception {
        mockMvc.perform(get("/no/such/route"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROUTE_NOT_FOUND"));
    }

    @Test
    void apiKeyProtectedRouteRejectsMissingKey() throws Exception {
        String token = login();
        createAndPublish(token, route("gw-apikey", "/gw/secured", stubUri("/stub"), "s3cr3t", null));

        mockMvc.perform(post("/gw/secured").content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        mockMvc.perform(post("/gw/secured").header("X-ESB-Api-Key", "s3cr3t").content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void rateLimitedRouteReturns429AfterFirstCall() throws Exception {
        String token = login();
        createAndPublish(token, route("gw-limited", "/gw/limited", stubUri("/stub"), null, 1));

        mockMvc.perform(post("/gw/limited").content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/gw/limited").content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    @Test
    void upstreamFailureYields502() throws Exception {
        String token = login();
        createAndPublish(token, route("gw-failing", "/gw/broken", stubUri("/fail"), null, null));

        mockMvc.perform(post("/gw/broken").content("{}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("GATEWAY_ERROR"));
    }

    @Test
    void invalidRouteDefinitionIsRejected() throws Exception {
        String token = login();
        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put("name", "bad-route");
        bad.put("path", "/admin/nope"); // reserved prefix
        bad.put("method", "*");
        bad.put("enabled", true);

        mockMvc.perform(post("/admin/api/v1/routes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void callLogsAreRecordedAsynchronously() throws Exception {
        String token = login();
        createAndPublish(token, route("gw-logged", "/gw/logged", stubUri("/stub"), null, null));
        mockMvc.perform(post("/gw/logged").content("{}")).andExpect(status().isOk());

        for (int attempt = 0; attempt < 20; attempt++) {
            String body = mockMvc.perform(get("/admin/api/v1/logs")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            if (body.contains("/gw/logged")) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("call log for /gw/logged was not persisted in time");
    }

    @Test
    void metricsSummaryIsAvailable() throws Exception {
        String token = login();
        mockMvc.perform(get("/admin/api/v1/metrics/summary").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCalls").isNumber())
                .andExpect(jsonPath("$.publishedRoutes").isNumber());
    }

    @Test
    void prometheusEndpointExposesExchangeMetrics() throws Exception {
        String token = login();
        createAndPublish(token, route("gw-metrics", "/gw/metrics", stubUri("/stub"), null, null));
        mockMvc.perform(post("/gw/metrics").content("{}")).andExpect(status().isOk());

        String prometheus = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(prometheus.contains("esb_exchange_total"), "expected esb_exchange_total metric");
    }

    private String stubUri(String path) {
        return "http://127.0.0.1:" + backendPort + path;
    }
}
