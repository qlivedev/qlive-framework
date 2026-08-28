package com.dataciders.app.scenarios;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Java-side equivalent of frontend/src/test-scenarios: edge cases and stress
 * fixtures that exercise the framework through qlive-test. Never imported
 * by com.dataciders.app.wiring - it must stay extractable on its own.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class HelloControllerScenarioTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void blankNameFallsBackToWorld() {
        String body = rest.getForObject("/api/hello?name=", String.class);
        assertThat(body).isEqualTo("Hello, world, from qlive!");
    }

    @Test
    void missingNameParamFallsBackToWorld() {
        String body = rest.getForObject("/api/hello", String.class);
        assertThat(body).isEqualTo("Hello, world, from qlive!");
    }

    @Test
    void unicodeNameRoundTrips() {
        String body = rest.getForObject("/api/hello?name=Üßé", String.class);
        assertThat(body).contains("Üßé");
    }
}
