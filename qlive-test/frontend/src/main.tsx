import * as React from "react";
import {createRoot} from "react-dom/client";
import {startup} from "@quinscape/qlive-ts";
import TestComponent from "./component/TestComponent";

document.addEventListener("DOMContentLoaded", async () => {

    await startup();

    const root = createRoot(document.getElementById("root")!);

    root.render(
        <React.StrictMode>
            <TestComponent/>
        </React.StrictMode>,
    );
});
