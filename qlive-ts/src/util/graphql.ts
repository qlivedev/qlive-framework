import {GraphQLQuery} from "../GraphQLQuery";
import config from "../config";


/**
 * Where in the query string an error is.
 */
export type GraphQLErrorLocation = {
    line: number
    column: number
}

/**
 * One entry of the "errors" of a GraphQL response.
 */
export type GraphQLError = {
    /**
     * Error message
     */
    message: string,
    /**
     * Query string location
     */
    locations?: GraphQLErrorLocation[]
    /**
     * Result key path of the field that failed, for an error while executing
     */
    path?: (string | number)[]
    /**
     * Whatever else the server says about the error. The QLive server puts its kind in `classification`:
     * "UNAUTHENTICATED" for a missing or expired login, "FORBIDDEN" for a missing role or CSRF token, and
     * graphql-java's own classifications, such as "ValidationError", for the rest.
     */
    extensions?: {
        classification?: string
        [name: string]: unknown
    }
}

/**
 * The rejection of a request the server answered with GraphQL errors. It carries them parsed, so a caller
 * can tell an ended session from a missing role or a broken query without reading the message.
 *
 *     catch (e)
 *     {
 *         if (e instanceof GraphQLResponseError && e.hasClassification("UNAUTHENTICATED")) ...
 *     }
 *
 * A request that got no GraphQL response at all -- the network, a server that isn't up -- rejects with
 * whatever went wrong instead.
 */
export class GraphQLResponseError extends Error
{
    /**
     * The errors of the response, as the server sent them
     */
    readonly errors: readonly GraphQLError[];

    /**
     * @param errors    the errors of the response, at least one
     */
    constructor(errors: readonly GraphQLError[])
    {
        super("GraphQL error: " + errors.map(error => error.message).join("; "));
        this.name = "GraphQLResponseError";
        this.errors = errors;
    }

    /**
     * Returns true if one of the errors has the given classification.
     *
     * @param classification    classification from the errors' extensions, e.g. "UNAUTHENTICATED"
     */
    hasClassification(classification: string): boolean
    {
        return this.errors.some(error => error.extensions?.classification === classification);
    }
}

/**
 * GraphQL standard for a response
 */
type GraphQLResponse = {
    /**
     * Contains the response result data, if any.
     */
    data: any;
    /**
     * Contains an errors GraphQLError instances
     */
    errors: GraphQLError[]
}

/**
 * Returns true if the given object is a GraphQL response.
 *
 * @param d input
 */
function isGraphQLResponse(d: unknown): d is GraphQLResponse
{
    // @ts-ignore
    const {data, errors} = d;
    return !!data || Array.isArray(errors)
}

/**
 * Returns the value of the first key of a GraphQL result.
 *
 * The wire format of a result is keyed by result key, while GraphQLQuery<T> promises
 * T for one execution of the query -- the value of its single selection. This is
 * where the two meet, which only works out because a query we inject or execute for
 * its value has exactly one top-level selection.
 */
export function firstValue(result: any)
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
 * Parameters for a GraphQL query. This is just the most generic description. Queries will complain loudly and in
 * great length if you don't give them their inputs.
 */
export type GraphQLParams =
    {
        [name: string]: any
    }

/**
 * Posts the given query to the server's /graphql endpoint and resolves with its
 * data, rejecting on a transport error or on any GraphQL error in the response. The
 * latter rejects with a GraphQLResponseError carrying the errors.
 *
 * This is the raw call: values go out and come back in their wire format, and the
 * result is the whole data object, keyed by result key. GraphQLQuery.execute()
 * runs the same request through the query's conversion map and unwraps the single
 * selection, and is what an application normally wants -- reach for this one when
 * the query has several top-level selections, or when the wire format is what you
 * are after.
 *
 * @param query     query to run, as a GraphQLQuery or its source
 * @param params    variables for the query, in wire format
 *
 * @returns the "data" member of the GraphQL response
 */
export default function graphql<T>(query: GraphQLQuery<T> | string, params: GraphQLParams): Promise<T> {
    let queryInstance: GraphQLQuery<T>
    if (typeof query === "string")
    {
        queryInstance = new GraphQLQuery<T>(query)
    } else
    {
        queryInstance = query;
    }

    const { contextPath, csrfToken } = config()

    return fetch(
        window.location.origin + contextPath + "/graphql",
        {
            method: "POST",
            credentials: "same-origin",
            headers: {
                "Content-Type": "application/json",
                "Accept": "application/json",

                // spring security enforces every POST request to carry a csrf token as either parameter or header
                [csrfToken!.header]: csrfToken!.value
            },
            body: JSON.stringify({
                query: queryInstance.query,
                variables: params
            })
        }
    )
        .then(response => response.json())
        .then(data => {
            if (!isGraphQLResponse(data))
            {
                return Promise.reject(new Error("Expected GraphQL response"));
            }

            if (data.errors && data.errors.length > 0)
            {
                return Promise.reject(new GraphQLResponseError(data.errors));
            }
            return data.data as T
        })
        .catch(error => {
            console.error("GraphQL error: ", error);
            return Promise.reject(error)
        })
}
