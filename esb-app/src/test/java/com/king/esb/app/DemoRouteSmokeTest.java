package com.king.esb.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke test for the {@code dev} profile: the seeded {@code demo-echo} route must mediate
 * {@code /demo/echo/**} to the platform-internal echo endpoint over real HTTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class DemoRouteSmokeTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void demoRouteMediatesToInternalEcho() {
        ResponseEntity<String> response = rest.postForEntity("/demo/echo", "{\"ping\":1}", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getHeaders().getFirst("X-ESB-Trace-Id"));
        String body = response.getBody();
        assertNotNull(body);
        assertTrue(body.contains("\"path\":\"/internal/echo\""), "echo endpoint should report its own path");
        assertTrue(body.contains("king-esb"), "the SET_HEADER step should tag the outbound request");
    }

    @Test
    void consoleIndexIsServed() {
        ResponseEntity<String> response = rest.getForEntity("/console/index.html", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
