import * as React from "react";
import {createRoot, Root} from "react-dom/client";

import {init, logStartup, QLiveBoostrap, QLiveConfig} from "./config";
import delay from "./util/delay";
import {isViteDev, viteBaseUrl} from "./util/viteEnv";
import {registerViews, ViewModules} from "./views";
import findRoot from "./util/findRoot";
import ErrorBoundary from "./component/ErrorBoundary";
import {loadViewForPath} from "./router";
import {FunctionComponent} from "react";

export interface StartupOptions
{
    /**
     * The application's view modules, as produced by import.meta.glob().
     *
     * Vite resolves import.meta.glob() at build time, relative to the file it appears in, and only accepts
     * literal patterns -- so the call has to happen in the application, which knows where its views live,
     * and the resulting map is handed to QLive here:
     *
     *     await startup({views: import.meta.glob("./app/**\/*.tsx"), ... })
     *
     * Without the `eager` option the map holds loader functions, so a view module is only fetched once
     * loadView() actually asks for it.
     *
     * Optional: an entry point that renders one fixed page -- a login page, say -- resolves no routes and
     * has nothing to register.
     */
    views?: ViewModules

    /**
     * The init function can be used to modify system behavior after the initialization is done but before a view
     * is rendered.
     *
     * @param config    Initialized config
     */
    init?: ( config: QLiveConfig ) => Promise<void>

    /**
     * In case there are no views defined, the user can give a render function to render the first components after
     * initialization. The component is rendered in strictMode if that is set to `true`.
     */
    render?: FunctionComponent<{ config: QLiveConfig}>

    /**
     * Defines the behavior for visiting the root URL (e.g. /app). If it is a string, we internally redirect to the view
     * with that name. Otherwise, it has to be a function component.
     */
    root?: string | FunctionComponent<any>

    /**
     * Whether to wrap the views in React.StrictMode. Default is `true` 
     */
    strictMode?: boolean
}

// Server responds 503 while it isn't ready to provide a complete QLiveConfig yet (e.g.
// booting, or -- in dev -- waiting on the first push from a Vite dev server that hasn't
// started). Retry until it is, rather than starting up with incomplete data.
async function fetchBootstrap(path : string): Promise<QLiveBoostrap>
{
    for (; ;)
    {
        // encodeURIComponent, not raw interpolation: the server compares this against the request URI of the
        // production route, which is percent-encoded. Interpolating raw would have the query parser decode one
        // level too many, so a path with an encoded character in it would arrive as a different string here
        // than the embedded route ever sees.
        const response = await fetch(`/api/bootstrap?path=${encodeURIComponent(path)}`, {
            method: "GET",
        });
        if (response.ok)
        {
            return await response.json() as QLiveBoostrap;
        }
        await delay(500);
    }
}


export async function startup(options: StartupOptions): Promise<Root>
{

    const {
        views,
        root,
        render,
        strictMode = true,
    } = options

    registerViews(views ?? {});

    const elem = document.getElementById("root-data");
    const text = elem?.textContent;

    let bsData: QLiveBoostrap | undefined;

    // In production, ViteIndexController has spliced the current QLiveConfig into the
    // placeholder. In `vite dev`, nobody touches that placeholder, so it stays empty --
    // fall back to fetching the same data live in that case (or if it's there but somehow
    // didn't parse).
    if (text)
    {
        try
        {
            bsData = JSON.parse(text) as QLiveBoostrap;
        } catch (e)
        {
            // fall through to the live fetch below
        }
    }

    // routeOf() would say whether this is the application root exactly, but it reads the config -- which is
    // what the bootstrap below initializes, and the redirect has to happen before that bootstrap is asked
    // for. So the base is recognized by the half that is knowable this early, Vite's, and the servlet context
    // path is left where it is: whatever precedes the base is the prefix the application is mounted under,
    // which is why appending to the current path builds the URL urlOf() would.
    const locationIsRoot = location.pathname.endsWith(viteBaseUrl());
    const rootIsViewName = typeof root === "string";
    if (locationIsRoot && rootIsViewName)
    {
        // Replacing the entry rather than pushing one keeps a URL the user never asked for out of the
        // history, and makes fetchBootstrap below ask for the view's own path, so the view finds its
        // injections. toLowerCase(), not toLocaleLowerCase(): the route table and the server both lower-case
        // invariantly, and a Turkish locale would turn "I" into a character neither of them knows.
        history.replaceState(null,  "", location.pathname + root.toLowerCase() + "/");
    }

    if (!bsData)
    {
        if (!isViteDev())
        {
            console.warn("Using injection fallback while not in vite dev mode")
        }

        bsData = await fetchBootstrap(location.pathname);
    }

    const config = await init(bsData);
    if (options.init)
    {
        await options.init(config)
    }

    logStartup(bsData);

    const rootContainer = createRoot(findRoot());
    // The URL picks the view: /home renders app/Home.tsx, /sub/view renders app/sub/View.tsx. Its chunk is
    // fetched here, at the moment the route needs it -- nothing loaded it up to this point.
    let View: React.ComponentType | null = null;

    if (locationIsRoot && root && !rootIsViewName)
    {
        View = root;
    }
    else if (views)
    {
        try
        {
            View = await loadViewForPath(location.pathname);
        }
        catch (e)
        {
            const ErrorView = config.errorView!;

            View = () => (
                <ErrorView error={ e }/>
            );
        }
    }
    else if (render)
    {
        View = () => render({config})
    }

    // Nothing to render is a valid outcome: an entry point that gave neither views nor root nor render asked
    // for initialization and nothing else, and renders into the root it is handed here itself.
    if (View)
    {
        const ViewWrapper = strictMode ? React.StrictMode : React.Fragment

        rootContainer.render(
            <ViewWrapper>
                <ErrorBoundary>
                    <View/>
                </ErrorBoundary>
            </ViewWrapper>,
        );
    }

    return rootContainer
}
