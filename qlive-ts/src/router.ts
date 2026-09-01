import type {ComponentType} from "react";
import config from "./config";
import {viteBaseUrl} from "./util/viteEnv";
import {loadView, routeNames, viewNameForRoute} from "./views";

/**
 * Returns the URL prefix the application is served under, with a trailing slash.
 *
 * The application's Vite `base` is the single source of truth here, so that the dev server and
 * ViteIndexController agree on where the application lives and a view has the same route either way. The
 * servlet context path is prepended because Vite knows nothing about it.
 */
export function appBase(): string
{
    return config().contextPath + viteBaseUrl()
}


/**
 * Converts a browser path name into an application route.
 *
 * @param pathName      location.pathname
 *
 * @return route without leading or trailing slash, lower case. "" for the application root itself.
 */
export function routeOf(pathName: string): string
{
    const base = appBase()

    const relative = pathName.startsWith(base) ? pathName.slice(base.length) : pathName.replace(/^\/+/, "")

    return relative.replace(/\/+$/, "").toLowerCase()
}


/**
 * Returns the URL for the given route, e.g. "sub/view" -> "/app/sub/view" in production.
 *
 * @param route     route without leading slash
 */
export function urlOf(route: string): string
{
    return appBase() + route
}


/**
 * Loads the view the given browser path addresses.
 *
 * @param pathName      location.pathname
 *
 * @return the view component
 */
export function loadViewForPath(pathName: string): Promise<ComponentType<any>>
{
    const route = routeOf(pathName)
    const name = viewNameForRoute(route)

    if (!name)
    {
        throw new Error(
            "No view for '" + pathName + "'. Known URLs: " + routeNames().map(urlOf).join(", ")
        )
    }

    return loadView(name)
}
