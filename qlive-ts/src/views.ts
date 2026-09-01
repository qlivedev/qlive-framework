import type {ComponentType} from "react";

/**
 * Map of module path to a function loading that module -- exactly what `import.meta.glob(pattern)` returns
 * when the `eager` option is not set.
 */
export type ViewModules = Record<string, () => Promise<unknown>>

type ViewModule = {
    default?: ComponentType<any>
}

let views = new Map<string, () => Promise<unknown>>()

// route (lower case, no leading slash) -> view name
let routes = new Map<string, string>()

/**
 * Determines the directory prefix all given module paths share, so that the registered view names come out as
 * "Home" and "sub/View" instead of "./app/Home.tsx" and "./app/sub/View.tsx". Derived from the paths rather
 * than configured because the application's glob pattern already decides where views live.
 *
 * @param paths     module paths
 *
 * @return common directory prefix including its trailing slash, "" if there is none
 */
function commonDirPrefix(paths: string[]): string
{
    let common: string[] | null = null

    for (const path of paths)
    {
        // drop the file name -- only directories can be part of the prefix
        const segments = path.split("/").slice(0, -1)

        if (common === null)
        {
            common = segments
            continue
        }

        let i = 0
        while (i < common.length && i < segments.length && common[i] === segments[i])
        {
            i++
        }
        common = common.slice(0, i)
    }

    return common && common.length ? common.join("/") + "/" : ""
}


function viewName(path: string, prefix: string): string
{
    const withoutPrefix = path.startsWith(prefix) ? path.slice(prefix.length) : path
    const dot = withoutPrefix.lastIndexOf(".")
    return dot > 0 ? withoutPrefix.slice(0, dot) : withoutPrefix
}


/**
 * Registers the application's view modules. Called by startup() with the map the application produced with
 * import.meta.glob().
 *
 * @param modules   view modules
 */
export function registerViews(modules: ViewModules): void
{
    const prefix = commonDirPrefix(Object.keys(modules))

    views = new Map()
    routes = new Map()
    for (const [path, loader] of Object.entries(modules))
    {
        const name = viewName(path, prefix)
        views.set(name, loader)

        // "Home" -> "home", "sub/View" -> "sub/view"
        const route = name.toLowerCase()
        const existing = routes.get(route)
        if (existing !== undefined)
        {
            throw new Error(
                "Views '" + existing + "' and '" + name + "' both map to the route '/" + route +
                "'. View names must differ by more than their case."
            )
        }
        routes.set(route, name)
    }
}


/**
 * Returns the name of the view registered for the given route, or null if there is none.
 *
 * @param route     route without leading slash, lower case ("home", "sub/view")
 */
export function viewNameForRoute(route: string): string | null
{
    return routes.get(route) ?? null
}


/**
 * Returns all routes that resolve to a view, sorted.
 */
export function routeNames(): string[]
{
    return [... routes.keys()].sort()
}


/**
 * Returns the names of all registered views, sorted.
 */
export function viewNames(): string[]
{
    return [... views.keys()].sort()
}


/**
 * Loads the view with the given name and returns its default export.
 *
 * The module is only fetched the first time its view is actually requested -- the application registers
 * loader functions, not modules.
 *
 * @param name  view name, e.g. "Home" or "sub/View"
 *
 * @return the view component
 */
export async function loadView(name: string): Promise<ComponentType<any>>
{
    const loader = views.get(name)
    if (!loader)
    {
        throw new Error("No view '" + name + "' registered. Known views: " + viewNames().join(", "))
    }

    const module = await loader() as ViewModule
    if (!module.default)
    {
        throw new Error("View '" + name + "' has no default export")
    }

    return module.default
}
