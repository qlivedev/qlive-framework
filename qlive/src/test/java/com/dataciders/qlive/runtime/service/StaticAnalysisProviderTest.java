package com.dataciders.qlive.runtime.service;

import com.dataciders.qlive.model.ts.ModuleFunctionReferences;
import com.dataciders.qlive.model.ts.TrackUsageData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
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

        // reported to the developer rather than thrown -- see DevStaticAnalysisProvider.merge()
        provider.replace(new ProdStaticAnalysisProvider(CLEAN).getTrackUsageData());

        assertThat(provider.getTrackUsageData(), is(notNullValue()));
    }


    /**
     * The dev server pushes the modules one editing round changed, not the whole analysis, so what it does
     * not mention has to survive the push.
     */
    @Test
    void keepsTheModulesAPushDoesNotMention()
    {
        final DevStaticAnalysisProvider provider = new DevStaticAnalysisProvider();

        provider.replace(new ProdStaticAnalysisProvider(CLEAN).getTrackUsageData());
        provider.merge(analysisOf("./sub/Q_Other"));

        final TrackUsageData merged = provider.getTrackUsageData();
        assertThat(merged.getModuleFunctionReferences("./sub/Q_Test"), is(notNullValue()));
        assertThat(merged.getModuleFunctionReferences("./sub/Q_Other"), is(notNullValue()));
    }


    /**
     * A full push is how the dev server states what the frontend now consists of, so a module it drops is a
     * module the analysis has to lose -- otherwise a deleted view would keep answering for its path.
     */
    @Test
    void dropsTheModulesAFullPushLeavesOut()
    {
        final DevStaticAnalysisProvider provider = new DevStaticAnalysisProvider();

        provider.replace(new ProdStaticAnalysisProvider(CLEAN).getTrackUsageData());
        provider.replace(analysisOf("./sub/Q_Other"));

        final TrackUsageData replaced = provider.getTrackUsageData();
        assertThat(replaced.getModuleFunctionReferences("./sub/Q_Test"), is(nullValue()));
        assertThat(replaced.getModuleFunctionReferences("./sub/Q_Other"), is(notNullValue()));
    }


    /**
     * Consumers cache what they worked out per analysis and recognize a new one by identity, so an update
     * that handed out the same instance would be an update they never see.
     */
    @Test
    void handsOutANewAnalysisPerUpdate()
    {
        final DevStaticAnalysisProvider provider = new DevStaticAnalysisProvider();

        provider.replace(new ProdStaticAnalysisProvider(CLEAN).getTrackUsageData());
        final TrackUsageData first = provider.getTrackUsageData();

        provider.merge(analysisOf("./sub/Q_Other"));

        assertThat(provider.getTrackUsageData(), is(not(sameInstance(first))));
    }


    /** What a push carrying one module with nothing tracked in it looks like. */
    private static TrackUsageData analysisOf(String module)
    {
        return new TrackUsageData(
            Map.of(module, new ModuleFunctionReferences(module, List.of(), Map.of(), Map.of(), Map.of()))
        );
    }
}
