import * as React from "react";
import {createRoot} from "react-dom/client";
import {loadViewForPath, startup} from "@quinscape/qlive-ts";
import TestComponent from "./component/TestComponent";

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
        views: import.meta.glob("./app/**/*.tsx"),
    });

    const root = createRoot(document.getElementById("root")!);

    // The URL picks the view: /home renders app/Home.tsx, /sub/view renders app/sub/View.tsx. Its chunk is
    // fetched here, at the moment the route needs it -- nothing loaded it up to this point.
    let View: React.ComponentType;
    try
    {
        View = await loadViewForPath(location.pathname);
    }
    catch (e)
    {
        View = () => <p>{ String(e) }</p>;
    }

    root.render(
        <React.StrictMode>
            <TestComponent/>
            <p>
                { String(Q_Foo) }
            </p>
            <View/>
        </React.StrictMode>,
    );
});
