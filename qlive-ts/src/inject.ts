import {GraphQLQuery} from "./GraphQLQuery";
import {GraphQLParams} from "./util/graphql";
import data from "./data";
import {isQueryDocumentType} from "./type-utils";
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

function getFirstValue(result: any)
{
    for (let key in result)
    {
        if (result.hasOwnProperty(key))
        {
            return result[key];
        }
    }
    return null;
}

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
    const result = injection.value;

    if (isQueryDocumentType(injection.type))
    {
        query.register(result)
    }
    
    return getFirstValue(result);
}
