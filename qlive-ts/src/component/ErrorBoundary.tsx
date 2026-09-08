import * as React from "react";
import {Component, ReactNode} from "react";

import config from "../config";

export type ErrorBoundaryProps = {
    children: ReactNode
}

type ErrorBoundaryState = {
    error: unknown
}

/**
 * Renders config().errorView in place of a subtree that threw while rendering.
 *
 * startup() puts one around the view it renders, so a view that throws takes down its own page and not the
 * browser tab. An application rendering into the root itself -- swapping views without a page load -- wraps
 * its own render the same way.
 *
 * A boundary keeps showing the error it caught, which is what makes it a boundary and not a retry. Give it a
 * `key` that changes with what is rendered below it, e.g. the route, and React discards the failed instance
 * along with its error:
 *
 *     <ErrorBoundary key={ routeOf(location.pathname) }>
 *         <View/>
 *     </ErrorBoundary>
 *
 * A class, because getDerivedStateFromError has no hook equivalent -- error boundaries are the one part of
 * React that still requires one.
 */
export default class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState>
{
    state: ErrorBoundaryState = {error: null}

    static getDerivedStateFromError(error: unknown): ErrorBoundaryState
    {
        return {error}
    }

    render(): ReactNode
    {
        const {error} = this.state

        if (!error)
        {
            return this.props.children
        }

        const ErrorView = config().errorView!

        return (
            <ErrorView error={ error }/>
        )
    }
}
