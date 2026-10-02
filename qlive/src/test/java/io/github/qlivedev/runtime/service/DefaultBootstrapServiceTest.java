package io.github.qlivedev.runtime.service;

import io.github.qlivedev.model.ts.TrackUsageData;
import io.github.qlivedev.runtime.QLiveException;
import io.github.qlivedev.util.JSONUtil;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers the mapping from a request path to the module serving it, which is what decides both the size of
 * the config a page gets and which injections run for it.
 */
class DefaultBootstrapServiceTest
{
    /**
     * The shape an application has: two entry points of its own, and views below the Vite base that the
     * frontend reaches through its route table.
     */
    private final static TrackUsageData ANALYSIS = analysis("""
        {
            "./main": { "requires": [], "calls": {} },
            "./login": { "requires": [], "calls": { "noSchema": [ [] ] } },
            "./app/Home": { "requires": [], "calls": {} },
            "./app/sub/View": { "requires": [], "calls": {} },
            "./component/ViteDevHome": { "requires": [], "calls": {} }
        }
        """);


    @Test
    void stripsEveryDescriptionOfAnIntrospectionResult()
    {
        final Map<String, Object> arg = new HashMap<>();
        arg.put("name", "config");
        arg.put("description", "the config");
        final Map<String, Object> field = new HashMap<>();
        field.put("name", "queryFoo");
        field.put("description", "all the Foos");
        field.put("args", List.of(arg));
        final Map<String, Object> type = new HashMap<>();
        type.put("name", "QueryType");
        type.put("description", "the queries");
        type.put("fields", List.of(field));

        final String stripped = JSONUtil.DEFAULT_GENERATOR.forValue(
            DefaultBootstrapService.withoutDescriptions(Map.of("types", List.of(type)))
        );

        assertThat(stripped, containsString("\"name\":\"queryFoo\""));
        assertThat(stripped, containsString("\"name\":\"config\""));
        assertThat(stripped.contains("the config") || stripped.contains("all the Foos") || stripped.contains("the queries"), is(false));
        // null rather than absent: what introspection answers where there is no description
        assertThat(stripped, containsString("\"description\":null"));
    }


    @Test
    void resolvesAnEntryPointByItsOwnRoute()
    {
        assertThat(DefaultBootstrapService.moduleForPath(ANALYSIS, "", "/login"), is("./login"));
    }


    @Test
    void resolvesAViewFromItsLowerCasedRoute()
    {
        // "/app/home" is what the browser has, "./app/Home" is what the module is called
        assertThat(DefaultBootstrapService.moduleForPath(ANALYSIS, "", "/app/home"), is("./app/Home"));
        assertThat(DefaultBootstrapService.moduleForPath(ANALYSIS, "", "/app/sub/view"), is("./app/sub/View"));
    }


    @Test
    void ignoresTheContextPathTheApplicationIsDeployedUnder()
    {
        assertThat(DefaultBootstrapService.moduleForPath(ANALYSIS, "/ctx", "/ctx/app/home"), is("./app/Home"));
        assertThat(DefaultBootstrapService.moduleForPath(ANALYSIS, "/ctx", "/ctx/login"), is("./login"));
    }


    @Test
    void ignoresATrailingSlash()
    {
        assertThat(DefaultBootstrapService.moduleForPath(ANALYSIS, "", "/app/home/"), is("./app/Home"));
    }


    @Test
    void resolvesNoModuleForTheApplicationRoot()
    {
        // the frontend renders its own landing page at the Vite base, which is no view of the application
        assertThat(DefaultBootstrapService.moduleForPath(ANALYSIS, "", "/app/"), is(nullValue()));
    }


    @Test
    void resolvesNoModuleForAnUnknownPath()
    {
        assertThat(DefaultBootstrapService.moduleForPath(ANALYSIS, "", "/app/nope"), is(nullValue()));
        assertThat(DefaultBootstrapService.moduleForPath(ANALYSIS, "", "/elsewhere"), is(nullValue()));
    }


    @Test
    void resolvesNoModuleOutsideTheViewRoot()
    {
        // A component is not a view, however much its name looks like a route.
        assertThat(DefaultBootstrapService.moduleForPath(ANALYSIS, "", "/app/vitedevhome"), is(nullValue()));
    }


    @Test
    void reportsARouteTwoModulesAnswerTo()
    {
        final TrackUsageData ambiguous = analysis("""
            {
                "./app/Home": { "requires": [], "calls": {} },
                "./app/home": { "requires": [], "calls": {} }
            }
            """);

        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> DefaultBootstrapService.moduleForPath(ambiguous, "", "/app/home")
        );

        assertThat(e.getMessage(), containsString("./app/Home"));
        assertThat(e.getMessage(), containsString("./app/home"));
    }


    private static TrackUsageData analysis(String usages)
    {
        return JSONUtil.DEFAULT_PARSER.parse(
            TrackUsageData.class,
            "{ \"usages\": " + usages + " }"
        );
    }
}
