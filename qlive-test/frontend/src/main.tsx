import * as React from "react";
import {createRoot} from "react-dom/client";
import {startup} from "@quinscape/qlive-ts";
import TestComponent from "./component/TestComponent";

// QLive ships its stylesheet as a separate artifact rather than pulling it in
// from the JS, so the application controls where it lands in the cascade.
// Import it before your own styles: everything in it sits in @layer qlive,
// which anything unlayered overrides regardless of specificity.
import "@quinscape/qlive-ts/styles.css";

document.addEventListener("DOMContentLoaded", async () => {

    await startup();

    const root = createRoot(document.getElementById("root")!);

    root.render(
        <React.StrictMode>
            <TestComponent/>
        </React.StrictMode>,
    );
});
