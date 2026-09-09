package com.dataciders.qlivetest.runtime;

import com.dataciders.qlive.model.bootstrap.Injection;
import com.dataciders.qlive.runtime.service.BootstrapService;
import com.dataciders.qlive.runtime.service.DevStaticAnalysisProvider;
import com.dataciders.qlive.runtime.service.ProdStaticAnalysisProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

/**
 * <p>
 *     Runs the real thing end to end: the track-usage data this application's own frontend build produced,
 *     resolved against its own GraphQL schema and executed against its own logic.
 * </p>
 * <p>
 *     The analysis is pushed into the dev provider the way {@code vite dev} pushes it, so what is exercised
 *     here is the path a developer is on. Its content is what {@code vite build} wrote, which is what the
 *     Maven build copies onto the classpath -- so the assertions below describe {@code src/app/Home.tsx} as
 *     it stands, and a change to the query it injects is meant to be visible here.
 * </p>
 */
@SpringBootTest
class BootstrapInjectionTest
{
    @Autowired
    private BootstrapService bootstrapService;

    @Autowired
    private DevStaticAnalysisProvider staticAnalysisProvider;


    @BeforeEach
    void pushBuiltAnalysis()
    {
        staticAnalysisProvider.replace(new ProdStaticAnalysisProvider().getTrackUsageData());
    }


    @Test
    void injectsWhatTheHomeViewDeclares()
    {
        // "/app/home" is the browser's location.pathname, "./app/Home" the module behind it, and Q_Foo the
        // query that module injects -- none of which is configured anywhere, all of it read off the source.
        final Map<String, Injection> injections = bootstrapService.provideInjectionData("/app/home");

        assertThat(injections.keySet(), contains("Q_Foo"));

        final Injection injection = injections.get("Q_Foo");
        assertThat(injection.getType(), is("FooDocument"));

        // The client reads the injection as the GraphQL result it is, keyed by result key -- "xxx" being the
        // alias Q_Foo gives its single selection.
        @SuppressWarnings("unchecked")
        final Map<String, Object> data = (Map<String, Object>) injection.getData();
        assertThat(data, hasKey("xxx"));

        @SuppressWarnings("unchecked")
        final Map<String, Object> document = (Map<String, Object>) data.get("xxx");
        assertThat(document.get("type"), is("Foo"));
        assertThat(document.get("config"), is(notNullValue()));
        assertThat((List<?>) document.get("rows"), is(not(List.of())));
    }


    @Test
    void injectsNothingForThePagesThatDeclareNothing()
    {
        // The login page is an entry point of its own and queries nothing.
        assertThat(bootstrapService.provideInjectionData("/login"), is(Map.of()));
    }
}
