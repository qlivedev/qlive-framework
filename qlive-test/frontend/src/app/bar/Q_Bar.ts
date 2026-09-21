import { Bar, BarDocument, BarLink, Baz } from "../../types";
import { GraphQLQuery, QueryDocumentMethods } from "@qlivedev/qlive-ts";

/**
 * The rows the edit view edits.
 *
 * Two things in here are the working set's rather than the view's: "version" on every row, which is the base
 * the merge holds a write to, and "id" on every link, which is what a removed association is deleted by. A
 * query whose rows are to be edited selects both, and a working set says so when they are missing.
 *
 * The result type below is generated from the query and rewritten whenever the selection changes, so
 * nothing may sit between it and the query -- this note included, which is why it stands above it.
 */
export type Q_BarResult = Pick<BarDocument,"type" | "config"> & {
    rows : Array<Pick<Bar,"id" | "name" | "num" | "description" | "version"> & {
        bazLinks : Array<Pick<BarLink,"id" | "version" | "barId" | "bazId"> & {
            baz : Pick<Baz,"id" | "name" | "version">
        }>
    }>
} & QueryDocumentMethods<Q_BarResult>

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

                bazLinks {
                    id
                    version
                    barId
                    bazId

                    baz {
                        id
                        name
                        version
                    }
                }
            }
        }
    }`
)
