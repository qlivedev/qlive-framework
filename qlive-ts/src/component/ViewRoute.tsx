import type {JSX, ReactNode} from "react";
import {createContext, useContext} from "react";
import {normalizeRoute, routeOf} from "../router";

/**
 * The route a ViewRoute provides, null outside of one.
 */
const ViewRouteContext = createContext<string | null>(null)

export type ViewRouteProps = {
    /**
     * Route of the view below, as routeOf() gives it for the view's own URL: "grid/sorting". Slashes at either end
     * and case don't matter.
     */
    route: string
    children: ReactNode
}

/**
 * Says which route the view below it is, for a view rendered anywhere but at its own URL: on a docs page, in a
 * Storybook story, in a component test, several views on one page.
 *
 * A view reads its injections by route -- useInjection(Q_FooList) in app/grid/Sorting.tsx reads
 * "grid/sorting/Q_FooList" -- and takes the route from the location where nothing says otherwise. startup()
 * renders a view at its own URL, so an application doesn't need this. FixtureScope provides one from its
 * fixture.
 *
 *     <ViewRoute route="grid/sorting">
 *         <Sorting/>
 *     </ViewRoute>
 */
export default function ViewRoute({route, children}: ViewRouteProps): JSX.Element
{
    return (
        <ViewRouteContext.Provider value={normalizeRoute(route)}>
            {children}
        </ViewRouteContext.Provider>
    )
}

/**
 * The route of the view the calling component belongs to: the nearest ViewRoute's, otherwise the location's.
 *
 * Rules of hooks apply.
 *
 * @returns route without leading or trailing slash, lower case
 */
export function useRoute(): string
{
    const route = useContext(ViewRouteContext)
    return route ?? routeOf(location.pathname)
}
