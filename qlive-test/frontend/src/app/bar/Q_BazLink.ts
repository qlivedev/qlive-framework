import { Baz, BazDocument } from "../../types";
import { GraphQLQuery, ClientQueryDocument } from "@qlivedev/qlive-ts";

/**
 * The bazes the link view offers.
 *
 * "version" is what makes a row a subject, as for the bars beside them. "description" is for the user: the rule
 * reading it reads the database.
 *
 * The result type below is generated from the query and rewritten whenever the selection changes, so
 * nothing may sit between it and the query -- this note included, which is why it stands above it.
 */
export type Q_BazLinkResult = Pick<BazDocument,"type" | "config"> & {
    rows : Array<Pick<Baz,"id" | "name" | "description" | "version">>
} & ClientQueryDocument<Q_BazLinkResult>

export const Q_BazLink = new GraphQLQuery<Q_BazLinkResult>(
    // language=GraphQL
    `query Q_BazLink($config: QueryConfig!) {
        queryBazDocument(config: $config) {
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
