import {GraphQLQuery} from "./GraphQLQuery";
import {firstValue, GraphQLParams} from "./util/graphql";
import data from "./data";
import {convertResultFromServer} from "./converter";
import {QueryDocument} from "./QueryDocument";

/**
 * Causes the injection of the given GraphQL result.
 *
 * @param query         GraphQL query
 * @param params        Parameters for the query
 *
 * @returns injected Value
 */

export type InjectParams = GraphQLParams & {
    /**
     * Used to disambiguate query results when the same named query gets used multiple times.
     */
    __id?: string
}

/** injection ids whose data has been converted, see inject() */
const converted = new Set<string>()

/**
 * Injects the data from the given query with the given optional params.
 *
 * The actual work for the data injection has already happened on the server side by the time this method actually
 * gets called. We use static analysis to detect the inject calls in the code base and prepare them before sending
 * each view.
 *
 * To receive the data, we need a injection id. Normally this is the query name as defined within the query definition.
 * If you need to inject the same query twice for the same view, at least one of the injections needs to provide a
 * __id parameter to disambiguate.
 *
 * @param query     GraphQLQuery
 * @param params    GraphQLParams including __id
 */
export default function inject<T>(query: GraphQLQuery<T>, params: InjectParams = {}): T
{
    const { __id } = params

    const injectionId = __id || query.queryName;
    const injection = data(injectionId);

    // The server ships the injection as the JSON it got out of GraphQL. Converting it
    // needs the selections of the query, which is only here now -- so it happens on
    // first use and the result replaces the raw data, an injection being read as often
    // as its view renders.
    if (!converted.has(injectionId))
    {
        injection.value = convertResultFromServer(injection.value, query.conversionMap)
        converted.add(injectionId)
    }

    const result = firstValue(injection.value) as T;

    if (result instanceof QueryDocument)
    {
        // gives the document the query it came from, which is what update() re-executes
        query.register(result)
    }

    return result;
}
