import {init, QLiveBoostrap, QLiveConfig} from "./config";
import delay from "./util/delay";
import {isViteDev} from "./util/viteEnv";
import {registerViews, ViewModules} from "./views";

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
     *  Current path
     */
    path: string,


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


export async function startup(options: StartupOptions): Promise<void>
{
    registerViews(options.views ?? {});

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

    if (!bsData)
    {
        if (!isViteDev())
        {
            console.warn("Using injection fallback while not in vite dev mode")
        }

        bsData = await fetchBootstrap(options.path);
    }

    return init(bsData);
}
