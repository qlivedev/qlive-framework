import {GraphQLQuery} from "../GraphQLQuery";

/*
 * Injected into the page by the server, not imported: the context path the
 * app is deployed under, and the CSRF token Spring Security requires on
 * every POST.
 */
declare const contextPath: string;
declare const csrfToken: { header: string, value: string };

type GraphQLErrorLocation = {
    line: number
    column: number
}
type GraphQLError = {
    message: string,
    locations?: GraphQLErrorLocation[]
}

type GraphQLResponse = {
    data: any;
    errors: GraphQLError[]
}

function isGraphQLResponse(d: unknown): d is GraphQLResponse
{
    // @ts-ignore
    const {data, errors} = d;
    return (typeof data !== "undefined" && Array.isArray(errors))
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

export type GraphQLParams =
    {
        [name: string]: any
    }

export default function graphql<T>(query: GraphQLQuery<T> | string, params: GraphQLParams): Promise<T> {
    let queryInstance: GraphQLQuery<T>
    if (typeof query === "string")
    {
        queryInstance = new GraphQLQuery<T>(query)
    } else
    {
        queryInstance = query;
    }

    return fetch(
        window.location.origin + contextPath + "/graphql",
        {
            method: "POST",
            credentials: "same-origin",
            headers: {
                "Content-Type": "application/json",
                "Accept": "application/json",

                // spring security enforces every POST request to carry a csrf token as either parameter or header
                [csrfToken.header]: csrfToken.value
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

            if (data.errors.length > 0)
            {
                return Promise.reject(new Error("GraphQL error: " + JSON.stringify(data.errors)));
            }
            return data.data as T
        })
        .catch(error => {
            console.error("GraphQL error: ", error);
            return Promise.reject(error)
        })
}
