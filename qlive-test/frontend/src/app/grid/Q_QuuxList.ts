import { QuuxDocument } from "../../types";
import { GraphQLQuery, ClientQueryDocument } from "@qlivedev/qlive-ts";

/**
 * The Quuxes with both numbers, which the sum column adds up in the browser, and the timestamp the date range
 * filters.
 */
export type Q_QuuxListResult = QuuxDocument & ClientQueryDocument<Q_QuuxListResult>

export const Q_QuuxList = new GraphQLQuery<Q_QuuxListResult>(
    // language=GraphQL
    `query Q_QuuxList($config: QueryConfig!) {
        queryQuuxDocument(config: $config) {
            type
            config
            rowCount
            rows {
                id
                name
                numA
                numB
                created
            }
        }
    }`
)
