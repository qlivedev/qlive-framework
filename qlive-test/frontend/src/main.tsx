import * as React from "react";
import { createRoot } from "react-dom/client";
import { startup } from "@quinscape/qlive-ts";

document.addEventListener("DOMContentLoaded", async () => {
    await startup();

    const root = createRoot(document.getElementById("root")!);

    root.render(
        <React.StrictMode>
            <h1>Test</h1>
        </React.StrictMode>,
    );
});
