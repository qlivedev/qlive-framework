import * as React from "react";
import "./style.css"
import {createRoot} from "react-dom/client";
import {startup, noSchema, config, findRoot } from "@quinscape/qlive-ts";

import "@quinscape/qlive-ts/styles.css";

noSchema();

/**
 * Login form for spring security's form login.
 *
 * A plain HTML form, deliberately: the POST is what authenticates the session, and letting the browser
 * submit it means the response -- a redirect to the requested view, or back here with ?error -- is handled
 * by the browser as well. The CSRF token spring security demands for that POST arrives with the QLive
 * bootstrap, which is why this page is served through IndexPageRenderer like any other view.
 */
function Login() {

}

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

    const { csrfToken } = config()
    const failed = new URLSearchParams(location.search).has("error")

    createRoot(findRoot()).render(
        // Posting to the path without the query string keeps ?error out of the next round-trip.
        <React.StrictMode>
            <form className="login" method="post" action={ location.pathname }>
                <h1>QLive Test</h1>
                {
                    failed && <p className="error">Invalid user name or password</p>
                }
                <label htmlFor="username">User</label>
                <input id="username" name="username" type="text" autoComplete="username" autoFocus required/>

                <label htmlFor="password">Password</label>
                <input id="password" name="password" type="password" autoComplete="current-password" required/>

                <input type="hidden" name={csrfToken!.param} value={csrfToken!.value}/>

                <button type="submit">Log in</button>
            </form>
        </React.StrictMode>
    )
});
