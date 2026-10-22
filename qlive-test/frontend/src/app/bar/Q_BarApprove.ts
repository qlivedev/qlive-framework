import { Bar, BarDocument } from "../../types";
import { GraphQLQuery, ClientQueryDocument } from "@qlivedev/qlive-ts";

/**
 * The bars the approve view offers.
 *
 * "version" is what makes a row a subject: issueAction() sends it with the id, and the server refuses with a
 * conflict where somebody wrote the row since it was read. "description" is here for the user, not for the rule
 * reading it -- the server reads it from the database.
 */
export type Q_BarApproveResult = Pick<BarDocument,"type" | "config"> & {
    rows : Array<Pick<Bar,"id" | "name" | "description" | "version">>
} & ClientQueryDocument<Q_BarApproveResult>

export const Q_BarApprove = new GraphQLQuery<Q_BarApproveResult>(
    // language=GraphQL
    `query Q_BarApprove($config: QueryConfig!) {
        queryBarDocument(config: $config) {
            type
            config
            rows {
                id
                name
                description
                version
            }
        }
    }`
)
