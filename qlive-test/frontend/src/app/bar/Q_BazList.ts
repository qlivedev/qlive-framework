import { Baz, BazDocument } from "../../types";
import { GraphQLQuery, QueryDocumentMethods } from "@quinscape/qlive-ts";

/**
 * Everything a Bar could be associated with, which is what the association editor offers. A list to pick
 * from rather than rows to edit, so nothing of it is registered with the working set.
 */
export type Q_BazListResult = Pick<BazDocument,"type" | "config"> & {
    rows : Array<Pick<Baz,"id" | "name" | "version">>
} & QueryDocumentMethods<Q_BazListResult>

export const Q_BazList = new GraphQLQuery<Q_BazListResult>(
    // language=GraphQL
    `query Q_BazList($config: QueryConfig!) {
        queryBazDocument(config: $config) {
            type
            config
            rows {
                id
                name
                version
            }
        }
    }`
)
