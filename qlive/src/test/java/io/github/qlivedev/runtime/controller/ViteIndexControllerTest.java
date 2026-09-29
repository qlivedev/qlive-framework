package io.github.qlivedev.runtime.controller;

import io.github.qlivedev.model.bootstrap.Injection;
import io.github.qlivedev.model.bootstrap.QLiveBoostrap;
import io.github.qlivedev.runtime.service.BootstrapService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ViteIndexControllerTest
{
    /// A server without the frontend's static analysis: what a backend started after the Vite dev server is.
    private final ViteIndexController controller = new ViteIndexController(
        new BootstrapService()
        {
            @Override
            public QLiveBoostrap provideConfig(CsrfToken csrfToken, String path)
            {
                return null;
            }


            @Override
            public Map<String, Injection> provideInjectionData(String path)
            {
                return null;
            }
        },
        null
    );


    @Test
    void marksTheBootstrapThatLacksTheStaticAnalysis()
    {
        assertNoStaticAnalysis(controller.bootstrap("/app/home", null));
    }


    @Test
    void marksTheUpdateThatLacksTheStaticAnalysis()
    {
        assertNoStaticAnalysis(controller.update("/app/home"));
    }


    private static void assertNoStaticAnalysis(ResponseEntity<String> response)
    {
        // Still the 503 the frontend retries, and marked, so that the Vite plugin can tell it apart from any
        // other and send the analysis.
        assertThat(response.getStatusCode(), is(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(
            response.getHeaders().getFirst(ViteIndexController.NOT_READY_HEADER),
            is(ViteIndexController.NO_STATIC_ANALYSIS)
        );
    }
}
