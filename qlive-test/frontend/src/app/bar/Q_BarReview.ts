import { Bar, BarDocument } from "../../types";
import { GraphQLQuery, ClientQueryDocument } from "@qlivedev/qlive-ts";

/**
 * The bars the review view edits and acts on.
 *
 * "version" serves both: the working set holds a save to it, and the action sends it with the id of its subject.
 * "created" is there for quoteBar, which takes the bar as BarInput, where it is required. "approved" is not: only
 * the action writes it, and a derived form would offer it for editing.
 *
 * The result type below is generated from the query and rewritten whenever the selection changes, so
 * nothing may sit between it and the query -- this note included, which is why it stands above it.
 */
export type Q_BarReviewResult = Pick<BarDocument,"type" | "config"> & {
    rows : Array<Pick<Bar,"id" | "name" | "num" | "description" | "created" | "version">>
} & ClientQueryDocument<Q_BarReviewResult>

export const Q_BarReview = new GraphQLQuery<Q_BarReviewResult>(
    // language=GraphQL
    `query Q_BarReview($config: QueryConfig!) {
        queryBarDocument(config: $config) {
            type
            config
            rows {
                id
                name
                num
                description
                created
                version
            }
        }
    }`
)
