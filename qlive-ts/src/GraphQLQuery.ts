import {QueryDocument} from "./QueryDocument";
import graphql, {GraphQLParams} from "./util/graphql";

const secret = Symbol("GraphQLQuery Secret")

export class GraphQLQuery<T>
{
    query: string;

    constructor(query: string)
    {
        this.query = query
    }

    /**
     * Registers this query with the given result
     * @param result    GraphQL result object to attack this query to
     */
    register(result: T): void
    {
        // The query is stashed on the result under a private symbol; T is
        // opaque here, so the symbol index has to be asserted.
        (result as Record<symbol, unknown>)[secret] = this
    }

    /**
     * Returns the GraphQL query registered with the given object
     * @param o     former GraphQL result with an attached query
     *
     * @returns query attached to object or null
     */
    static access<T>(o: T): GraphQLQuery<T> | null
    {
        return (o as Record<symbol, unknown>)[secret] as GraphQLQuery<T> || null
    }

    execute(params: GraphQLParams): Promise<T>
    {
        return graphql<T>(this, params).then((data: T): T => {

            return data;
        });
    }
}
