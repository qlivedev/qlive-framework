import * as React from "react";

import config from "../config";

export type LogoutProps = {
    /**
     * Rendered inside the submit button. Defaults to "Logout".
     */
    children?: React.ReactNode

    /**
     * "button" renders the submit as a normal button, "link" makes it look like the surrounding text's
     * anchors instead -- for a nav bar or a sentence where a boxed button would stand out for no reason.
     * Ignored once `className` is given explicitly.
     */
    variant?: "button" | "link"

    /**
     * Overrides the class the variant would otherwise pick.
     */
    className?: string
}

/**
 * Logs the current user out.
 *
 * A form, not a link: /logout is Spring Security's default logout endpoint and, like every state-changing
 * request the application does not explicitly exempt, it demands the CSRF token this component carries as a
 * hidden field -- the same way the login form on the login page carries it. `variant="link"` only changes
 * how the submit button is styled, not what it is -- a button inside a form, so it keeps working with
 * keyboard activation and without JavaScript.
 */
export default function Logout({ children = "Logout", variant = "button", className }: LogoutProps)
{
    const { csrfToken } = config()

    return (
        <form method="post" action="/logout">
            <input type="hidden" name={ csrfToken!.param } value={ csrfToken!.value }/>
            <button type="submit" className={ className ?? (variant === "link" ? "qlive-link-button" : "btn") }>
                { children }
            </button>
        </form>
    )
}
