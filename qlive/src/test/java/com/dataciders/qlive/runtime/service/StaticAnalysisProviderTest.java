package com.dataciders.qlive.runtime.service;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers what the two providers do with an application that injects where it may not: the production one
 * refuses the build, the dev one says so and keeps serving.
 */
class StaticAnalysisProviderTest
{
    /**
     * Analysis of an application whose component calls useInjection().
     */
    private final static String OUTSIDE_VIEWS = "track-usage-outside-views.json";

    /**
     * Analysis of an application that injects nowhere at all, which is fine.
     */
    private final static String CLEAN = "test-ts-root/track-usage.json";


    @Test
    void refusesToStartOnAnInjectionOutsideAView()
    {
        final IllegalStateException e = assertThrows(
            IllegalStateException.class,
            () -> new ProdStaticAnalysisProvider(OUTSIDE_VIEWS)
        );

        assertThat(e.getMessage(), containsString("./component/Widget"));
        assertThat(e.getMessage(), containsString("useInjection()"));
    }


    @Test
    void startsOnAnApplicationThatInjectsWhereItMay()
    {
        assertDoesNotThrow(() -> new ProdStaticAnalysisProvider(CLEAN));
    }


    @Test
    void keepsServingInDevSoTheEditCanBeFinished()
    {
        final DevStaticAnalysisProvider provider = new DevStaticAnalysisProvider();

        // reported to the developer rather than thrown -- see DevStaticAnalysisProvider.update()
        provider.update(new ProdStaticAnalysisProvider(CLEAN).getTrackUsageData());

        assertThat(provider.getTrackUsageData(), is(notNullValue()));
    }
}
