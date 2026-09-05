import * as React from "react";
import "./style.css"
import {createRoot} from "react-dom/client";
import {startup} from "@quinscape/qlive-ts";
import Login from "./component/Login";

import "@quinscape/qlive-ts/styles.css";

/**
 * Entry point of the login page (login.html), which spring security serves under /login.
 *
 * The same startup as main.tsx, minus everything the login page has no use for: there is exactly one thing
 * to render here, so no view modules are registered and no route is resolved. What it does share is the
 * bootstrap -- embedded in the page in production, fetched from /api/bootstrap in vite dev -- because that
 * is where the CSRF token the login form has to send comes from.
 */
document.addEventListener("DOMContentLoaded", async () => {

    await startup({
        path: location.pathname,
    });

    const container = document.getElementById("root");
    if (!container){
        throw new Error("View must have a #root element")
    }

    createRoot(container).render(
        <React.StrictMode>
            <Login/>
        </React.StrictMode>,
    );
});
