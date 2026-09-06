package com.dataciders.qlive.runtime.service;

import com.dataciders.qlive.model.ts.TrackUsageData;

/// Provides the result of the static analysis of the application's TypeScript
/// sources: which modules call which tracked functions and with what arguments.
///
/// The results are used to drive data injection and for simple declarative purposes like `noSchema`.
///
/// Where that data comes from differs per environment -- pushed live by the Vite dev server's plugin in dev,
/// read from the built `track-usage.json` in production -- which is exactly why the consumers get it through
/// this interface instead of one of those two mechanisms.
///
@FunctionalInterface
public interface StaticAnalysisProvider
{
    /// The current track-usage data, or `null` if it is not available yet.
    ///
    /// `null` means not ready, never "this application has no analysis": callers report that the server
    /// cannot serve the request yet and let the caller retry, rather than guessing at an answer they would
    /// have to contradict a moment later. Only the dev implementation can return it, and only until the Vite
    /// dev server pushes its first snapshot -- the production one fails at startup instead, because there a
    /// missing analysis is a build error rather than a matter of timing.
    TrackUsageData getTrackUsageData();
}
