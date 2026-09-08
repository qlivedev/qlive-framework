import * as React from "react";
import { noSchema, startup} from "@quinscape/qlive-ts";

import "@quinscape/qlive-ts/styles.css";
import "./style.css"

noSchema();

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
        render: ({ config }) => {

            const { csrfToken } = config
            const failed = new URLSearchParams(location.search).has("error")

            return (
                <form className="login" method="post" action={location.pathname}>
                    <h1>QLive Test</h1>
                    {
                        failed && <p className="error">Invalid user name or password</p>
                    }
                    <label htmlFor="username">User</label>
                    <input id="username" name="username" type="text" autoComplete="username" autoFocus required/>

                    <label htmlFor="password">Password</label>
                    <input id="password" name="password" type="password" autoComplete="current-password" required/>

                    <div className="remember-me">
                        {/*
                          * "remember-me" is spring security's default name for this parameter, and what
                          * RememberMeAuthenticationFilter looks for. Ticking it makes the login survive the
                          * session, backed by the app_login table (see DefaultPersistentTokenRepository).
                          */}
                        <input id="remember-me" name="remember-me" type="checkbox"/>
                        <label htmlFor="remember-me">Stay logged in on this computer</label>
                    </div>

                    <input type="hidden" name={csrfToken!.param} value={csrfToken!.value}/>

                    <button type="submit">Log in</button>
                </form>
            );
        }
    });
});
