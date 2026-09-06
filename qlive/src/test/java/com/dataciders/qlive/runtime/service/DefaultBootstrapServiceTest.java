package com.dataciders.qlive.runtime.service;

import com.dataciders.qlive.model.ts.TrackUsageData;
import com.dataciders.qlive.runtime.QLiveException;
import de.quinscape.spring.jsview.util.JSONUtil;
import org.junit.jupiter.api.Test;

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
