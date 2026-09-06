import * as React from "react";
import "./style.css"
import {createRoot} from "react-dom/client";
import {findRoot, loadViewForPath, startup } from "@quinscape/qlive-ts";
import TestComponent from "./component/TestComponent";
import ViteDevHome from "./component/ViteDevHome";

import {Q_Foo, Q_FooResult} from "./app/Q_Foo";


// QLive ships its stylesheet as a separate artifact rather than pulling it in
// from the JS, so the application controls where it lands in the cascade.
// Import it before your own styles: everything in it sits in @layer qlive,
// which anything unlayered overrides regardless of specificity.
import "@quinscape/qlive-ts/styles.css";

document.addEventListener("DOMContentLoaded", async () => {

    // Vite resolves import.meta.glob() at build time and only accepts literal patterns, so the view lookup
    // has to be declared here rather than inside QLive. Without `eager` the map holds loader functions --
    // each view becomes its own chunk and is fetched the first time loadView() asks for it.
    await startup({
        path: location.pathname,
        views: import.meta.glob("./app/**/*.tsx"),
    });

    const root = createRoot(findRoot());

    // The URL picks the view: /home renders app/Home.tsx, /sub/view renders app/sub/View.tsx. Its chunk is
    // fetched here, at the moment the route needs it -- nothing loaded it up to this point.
    let View: React.ComponentType;
    if (location.pathname === "/app/")
    {
        View = ViteDevHome
    }
    else
    {
        try
        {
            View = await loadViewForPath(location.pathname);
        }
        catch (e)
        {
            View = () => <p>{ String(e) }</p>;
        }
    }

    root.render(
        <React.StrictMode>
            <View/>
        </React.StrictMode>,
    );
});
