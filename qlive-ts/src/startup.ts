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

export function startup() : void {

    const elem = document.getElementById("root-data");
    const data = !!elem && JSON.parse(elem.innerHTML)

    init(data as QLiveConfig);

    // return webpackCtx("./Home.tsx").then(result => {
    //        console.log("STARTUP", result)
    //     return result.default
    // }) as Promise<Function>
}
