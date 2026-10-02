import {GraphQLQuery} from "./GraphQLQuery";
import {firstValue, GraphQLParams} from "./util/graphql";
import data, {injectionSource, storeInjection} from "./data";
import {convertResultFromServer} from "./converter";
import {QueryDocument} from "./QueryDocument";
import {isFixture} from "./config";
import {holdRows} from "./localDocument";
import {isViteDev} from "./util/viteEnv";
import {noteInjection} from "./fixture/notes";
import {routeOf} from "./router";

/**
 * GraphQL types plus a declarative injection id that is declared with it to disambiguate injection
 * but which is never sent as GraphQL parameter.
 */
export type InjectParams = GraphQLParams & {
    /**
     * Used to disambiguate query results when the same named query gets used multiple times.
     */
    __id?: string
}

/**
 * Reads the data the server injected for the given query, converting it on first use.
 *
 * Framework-internal: this is the plain read, with nothing subscribed to what it returns,
 * so an update() of the document it yields would never reach a view. Applications call
 * useInjection(), which is this plus the subscription.
 *
 * To find the data, we need an injection id: the route of the view, a slash, and the
 * query name as defined within the query definition -- "grid/sorting/Q_FooList". The
 * server keys what it injects the same way, from the module of the view. If you need to
 * inject the same query twice for the same view, at least one of the injections needs to
 * provide a __id parameter to disambiguate; it replaces the query name.
 *
 * @param query     GraphQLQuery
 * @param params    GraphQLParams including __id
 * @param route     route of the view injecting, the location's by default; useInjection()
 *                  passes the one a ViewRoute gives
 *
 * @returns the injected value, a QueryDocument where the query selects one
 */
export default function inject<T>(
    query: GraphQLQuery<T>,
    params: InjectParams = {},
    route: string = routeOf(location.pathname)
): T
{
    const id = injectionId(route, query, params)
    if (isViteDev())
    {
        // what the fixture recorder needs to query the injection again
        noteInjection(id, query, params)
    }

    let injection = data(id);

    if (!injection)
    {
        const source = injectionSource(id)
        if (!source)
        {
            throw new Error(
                "No injection '" + id + "' in the data of this page. The server injects what the " +
                "static analysis recorded of the view's useInjection() calls, and it records a call only if " +
                "it can evaluate its arguments -- the dev server's output names a call it left out."
            )
        }

        // The server ships the injection as the JSON it got out of GraphQL. Converting it
        // needs the selections of the query, which is only here now -- so it happens on
        // first use and the converted injection is what every later read of that id gets,
        // an injection being read as often as its view renders.
        injection = {
            value: convertResultFromServer(source.data, query.conversionMap),
            type: source.type,
            meta: source.meta
        }

        const document = firstValue(injection.value)
        if (isFixture() && document instanceof QueryDocument)
        {
            // A fixture's document holds every row, and shows the page its config asks for out of them.
            holdRows(document)
        }

        storeInjection(id, injection)
    }

    const result = firstValue(injection.value) as T;

    if (result instanceof QueryDocument)
    {
        // gives the document the query it came from, which is what update() re-executes
        query.register(result)
    }

    return result;
}


/**
 * The id the injection of the given query is stored under on the given route: the route, a
 * slash, and the __id of the parameters where they have one, otherwise the query name.
 *
 * @param route     route of the view, as routeOf() gives it
 * @param query     GraphQLQuery
 * @param params    parameters as given to useInjection()
 */
export function injectionId(route: string, query: GraphQLQuery<any>, params: InjectParams): string
{
    return route + "/" + (params.__id || query.queryName)
}
