import { Bar, BarDocument, Baz } from "../../types";
import { GraphQLQuery, ClientQueryDocument } from "@qlivedev/qlive-ts";

/**
 * The rows the edit view edits.
 *
 * Two things in here are the working set's rather than the view's: "version" on every row, which is the base
 * the merge holds a write to, and "id" on every Baz in "bazs", which is what an association names. A query
 * whose rows are to be edited selects both, and a working set says so when they are missing.
 *
 * The result type below is generated from the query and rewritten whenever the selection changes, so
 * nothing may sit between it and the query -- this note included, which is why it stands above it.
 */
export type Q_BarResult = Pick<BarDocument,"type" | "config"> & {
    rows : Array<Pick<Bar,"id" | "name" | "num" | "description" | "version"> & {
        bazs : Array<Pick<Baz,"id" | "name">>
    }>
} & ClientQueryDocument<Q_BarResult>

export const Q_Bar = new GraphQLQuery<Q_BarResult>(
    // language=GraphQL
    `query Q_Bar($config: QueryConfig!) {
        queryBarDocument(config: $config) {
            type
            config
            rows {
                id
                name
                num
                description
                version

                bazs {
                    id
                    name
                }
            }
        }
    }`
)
