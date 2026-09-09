package com.dataciders.qlive.runtime;

/// The URL prefixes QLive and the frontend build have to agree on.
public final class QLivePaths
{
    private QLivePaths()
    {
        // no instances
    }

    /// Where the application is mounted, i.e. the frontend's Vite `base`, with a trailing slash.
    ///
    /// Both sides of a view lookup read this: the controller that serves every route below it, and the
    /// bootstrap service that turns such a route back into the view module the frontend would load for it.
    ///
    /// A compile-time constant rather than a configuration property, because the request mappings that serve
    /// this prefix are annotations. An application that changes its Vite `base` changes this with it.
    public final static String APP_BASE = "/app/";

    /// Where the application's views live, as a track-usage module prefix with a trailing slash.
    ///
    /// The counterpart of {@link #APP_BASE} on the module side: the directory of the view glob an
    /// application passes to startup(), which is what views.ts strips off to name a view. `/app/sub/view` is
    /// served by "./app/sub/View" because of these two constants and nothing else.
    ///
    /// The glob itself cannot be read from here -- it is resolved by Vite at build time and never leaves the
    /// frontend -- so the convention is stated here instead. It is also what says where useInjection() may
    /// be called: below this, and nowhere else.
    public final static String VIEW_ROOT = "./app/";

    /// Ant pattern covering every endpoint QLive maps for development only, e.g.
    /// {@link com.dataciders.qlive.runtime.controller.GraphQLController#GRAPHQL_DEV_URI} and
    /// {@link com.dataciders.qlive.runtime.controller.TrackUsageDevController#TRACK_USAGE_DEV_URI}.
    ///
    /// These are unauthenticated and exempt from CSRF, which is what makes them usable from the Vite dev
    /// server, and exactly why an application's security configuration has to refuse them outside the dev
    /// profile. Spring maps a handler method whatever the profile -- `@Profile` is evaluated for bean
    /// definitions, not for the request mappings of a bean that exists -- so security is what decides
    /// whether they can be reached, and nothing else is.
    ///
    /// Named here so that an application says which rule applies to them without having to know what QLive
    /// maps below the prefix.
    public final static String DEV_URIS = "/_dev/**";
}
