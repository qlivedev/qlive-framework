import {init, QLiveConfig} from "./config";

// Webpack/rspack-specific dynamic-import mechanism (`import.meta.webpackContext`),
// used here to lazily load a page component by name at runtime. It has no
// direct Vite equivalent by that name -- import.meta.webpackContext
// categorically cannot work unmodified under Vite.
//
// Vite's equivalent capability is import.meta.glob()
// (https://vite.dev/guide/features.html#glob-import): statically analyzed
// at build time the same way, but it returns a map of matched file paths
// to import functions instead of a context function you call directly.
//
// Not reimplemented now -- pick this back up with import.meta.glob() when
// this dynamic-loading path is needed again.
//
// type ImportMetaWebpackContext = {
//     (name : string) : Promise<any>
//     keys: () => string[]
//     resolve: (path: string) => void
// }

export async function startup() : Promise<void> {

    const elem = document.getElementById("root-data");
    const text = elem?.textContent;

    let data : QLiveConfig | undefined;

    // In production, ViteIndexController has spliced the current QLiveConfig into the
    // placeholder. In `vite dev`, nobody touches that placeholder, so it stays empty --
    // fall back to fetching the same data live in that case (or if it's there but somehow
    // didn't parse).
    if (!import.meta.env.DEV && text) {
        try {
            data = JSON.parse(text) as QLiveConfig;
        } catch (e) {
            // fall through to the live fetch below
        }
    }

    if (!data) {
        const response = await fetch("/api/bootstrap");
        data = await response.json() as QLiveConfig;
    }

    init(data);

    // return webpackCtx("./Home.tsx").then(result => {
    //        console.log("STARTUP", result)
    //     return result.default
    // }) as Promise<Function>
}
