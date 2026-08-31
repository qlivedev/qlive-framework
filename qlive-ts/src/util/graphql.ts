import {GraphQLQuery} from "../GraphQLQuery";

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
            const t = data as T;
            queryInstance.register(t);
            return t
        })
        .catch(error => {
            console.error("GraphQL error: ", error);
            return Promise.reject(error)
        })
}
