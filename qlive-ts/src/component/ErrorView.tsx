import * as React from "react";

/**
 * What an error view is handed.
 */
export type ErrorViewProps = {
    /**
     * The error QLive could not carry on from. Typed as unknown because that is what a `catch` binding and a
     * React error boundary both hand on -- everything QLive throws itself is an Error.
     */
    error: unknown
}

/**
 * The error view an application gets until it sets one of its own.
 *
 * Deliberately bare: it states the error and leaves it there, because a page that belongs to an application
 * is the application's to design. Replace it by assigning to config.errorView in startup()'s init hook.
 */
export default function DefaultErrorView({error}: ErrorViewProps)
{
    return (
        <p className="qlive-error">
            {
                error instanceof Error ? error.message : String(error)
            }
        </p>
    )
}
