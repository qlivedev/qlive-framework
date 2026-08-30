import {GraphQLQuery} from "./GraphQLQuery";
import {GraphQLParams} from "./util/graphql";
import {field, value} from "./FilterDSL";

/**
 * Causes the injection of the given GraphQL result.
 *
 * @param query         GraphQL query
 * @param params        Parameters for the query
 *
 * @returns injected Value
 */
export default function inject<T>(query: GraphQLQuery<T>, params: GraphQLParams): T {
    const data = {
        config: {
            offset: 0,
            pageSize: 20,
            condition: field("name").eq(value("John")),
            sortFields: ["name"]
        },
        rows: []
    }
    return data as T;
}
