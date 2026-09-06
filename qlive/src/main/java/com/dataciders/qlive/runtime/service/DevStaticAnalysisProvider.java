package com.dataciders.qlive.runtime.service;

import com.dataciders.qlive.model.ts.TrackUsageData;

/// Provides a dev implementation of {@link StaticAnalysisProvider} which holds the last static analysis snapshot
/// pushed by the Vite dev server.
///
/// `vite dev` never writes track-usage.json to disk, so there is no file to read in dev -- the plugin POSTs
/// snapshots to {@link com.dataciders.qlive.runtime.controller.TrackUsageDevController} as the developer
/// edits, and this is where they land so the rest of the server can see them.
public class DevStaticAnalysisProvider
    implements StaticAnalysisProvider
{
    /// Written by the request thread handling a push, read by request threads serving pages. Volatile rather
    /// than synchronized: a reader either sees the previous snapshot or the new one, and both are consistent
    /// in themselves.
    private volatile TrackUsageData trackUsageData;


    @Override
    public TrackUsageData getTrackUsageData()
    {
        return trackUsageData;
    }


    public void update(TrackUsageData trackUsageData)
    {
        this.trackUsageData = trackUsageData;
    }
}
