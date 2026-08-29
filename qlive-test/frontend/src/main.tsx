import * as React from "react";
import { createRoot } from "react-dom/client";

document.addEventListener("DOMContentLoaded", () => {
    const root = createRoot(document.getElementById("root")!);

    root.render(
        <React.StrictMode>
            <h1>Test</h1>
        </React.StrictMode>,
    );
});
