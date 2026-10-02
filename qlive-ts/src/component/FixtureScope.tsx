import {ReactNode, useMemo} from "react";
import {addFixture, QLiveFixture} from "../config";
import ViewRoute from "./ViewRoute";

export type FixtureScopeProps = {
    /**
     * The fixture the view below runs on, e.g. one recorded from a running application in dev mode
     */
    fixture: QLiveFixture
    children: ReactNode
}

/**
 * Runs the view below on the given fixture, at the fixture's route. For whatever renders a view without startup():
 * an island on a static page, a Storybook story, a component test.
 *
 *     <FixtureScope fixture={sortingFixture}>
 *         <Sorting/>
 *     </FixtureScope>
 *     <FixtureScope fixture={filtersFixture}>
 *         <Filters/>
 *     </FixtureScope>
 *
 * Any number of scopes can share a page, one per route: the fixture goes in next to the others with addFixture(),
 * and the view reads its injections by the route a ViewRoute gives it. A fixture that doesn't fit the page -- another
 * application's, another recording for a route already there, a page running on a server -- throws while rendering,
 * for the nearest ErrorBoundary to show.
 *
 * Two scopes with the same fixture share its documents: sort the view in one, and the other sorts with it.
 */
export default function FixtureScope({fixture, children}: FixtureScopeProps)
{
    // While rendering, not in an effect: the view below reads its injections on its first render. Adding the same
    // fixture again changes nothing, so a second render under StrictMode doesn't either.
    useMemo(() => addFixture(fixture), [fixture])

    return (
        <ViewRoute route={fixture.route}>
            {children}
        </ViewRoute>
    )
}
