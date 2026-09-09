package com.dataciders.qlive.runtime.service;

import com.dataciders.qlive.model.ts.ModuleFunctionReferences;
import com.dataciders.qlive.model.ts.TrackUsageData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/// Provides a dev implementation of {@link StaticAnalysisProvider} which holds the static analysis pushed by
/// the Vite dev server.
///
/// `vite dev` never writes track-usage.json to disk, so there is no file to read in dev -- the plugin POSTs
/// the analysis to {@link com.dataciders.qlive.runtime.controller.TrackUsageDevController} as the developer
/// edits, and this is where it lands so the rest of the server can see it.
///
/// The plugin sends one editing round's changed modules rather than the whole analysis, so this class holds
/// the modules and {@link #merge} folds an update into them.
public class DevStaticAnalysisProvider
    implements StaticAnalysisProvider
{
    private final static Logger log = LoggerFactory.getLogger(DevStaticAnalysisProvider.class);

    /// The modules as pushed so far. Concurrent because a push writes it while requests serving pages read
    /// through the {@link TrackUsageData} view below, which is this same map.
    private final Map<String, ModuleFunctionReferences> usages = new ConcurrentHashMap<>();

    /// The view of {@link #usages} handed out, `null` until the first push. Replaced by a new instance on
    /// every update even though its content is the same map, because consumers cache per analysis and
    /// recognize a new one by identity -- see {@link InjectionService}.
    private volatile TrackUsageData trackUsageData;

    /// What the last report said, so that the same mistake is not restated on every keystroke.
    private volatile List<String> reportedOutsideViews = List.of();


    @Override
    public TrackUsageData getTrackUsageData()
    {
        return trackUsageData;
    }


    /// Replaces the whole analysis with the given one, which is what the dev server pushes when it starts and
    /// whenever this provider has nothing to merge into.
    ///
    /// The modules that stay are updated before the ones that went are dropped, so that a request landing in
    /// between sees a complete analysis rather than an empty one.
    public void replace(TrackUsageData analysis)
    {
        merge(analysis);
        usages.keySet().retainAll(modulesOf(analysis).keySet());
    }


    /// Folds one update -- typically the modules changed by a single save -- into the analysis held here.
    /// Modules it does not mention keep the data they were last pushed with.
    public void merge(TrackUsageData update)
    {
        usages.putAll(modulesOf(update));
        this.trackUsageData = new TrackUsageData(usages);

        // Where the production build refuses to start, dev says so and carries on: the developer is mid-edit,
        // and the rest of the application is still worth serving while this one call gets moved.
        final List<String> outsideViews = InjectionService.injectionsOutsideViews(this.trackUsageData);
        if (!outsideViews.equals(reportedOutsideViews))
        {
            reportedOutsideViews = outsideViews;

            if (!outsideViews.isEmpty())
            {
                log.error(
                    "{} Until then those injections are not prepared, and the views rendering those " +
                        "components will fail on them.",
                    InjectionService.describeInjectionsOutsideViews(outsideViews)
                );
            }
            else
            {
                log.info("All injections sit in views again");
            }
        }
    }


    /// A push with no "usages" at all is a push that changed nothing, not a broken one.
    private static Map<String, ModuleFunctionReferences> modulesOf(TrackUsageData data)
    {
        return Objects.requireNonNullElse(data.getModuleFunctionReferences(), Map.of());
    }
}
