import { GraphQLQuery, QueryDocumentMethods } from "@quinscape/qlive-ts";
import { Bar, BarDocument, BarLink, Baz, BazDocument } from "../../types";

/**
 * One association as the edit view reads one, and as it writes one back.
 *
 * The identity fields are optional because a new association is written as the row it is about --
 * `[...bar.bazLinks, {baz}]` -- and the merge turns that into a BarLink insert with both foreign keys. What
 * comes out of the query has all of them, which is what a removed association is deleted by.
 */
export type Q_BarEditLink = {
    baz: Pick<Baz, "id" | "name" | "version">
} & Partial<Pick<BarLink, "id" | "version" | "barId" | "bazId">>

export type Q_BarEditRow = Pick<Bar, "id" | "name" | "num" | "description" | "version"> & {
    bazLinks: Q_BarEditLink[]
}

export type Q_BarEditResult = Pick<BarDocument, "type" | "config"> & {
    rows: Q_BarEditRow[]
} & QueryDocumentMethods<Q_BarEditResult>

/**
 * The rows the edit view edits.
 *
 * Two things in here are the working set's rather than the view's: "version" on every row, which is the base
 * the merge holds a write to, and "id" on every link, which is what a removed association is deleted by. A
 * query whose rows are to be edited selects both, and a working set says so when they are missing.
 */
export const Q_BarEdit = new GraphQLQuery<Q_BarEditResult>(
    // language=GraphQL
    `query Q_BarEdit($config: QueryConfig!) {
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

export type Q_BazListResult = Pick<BazDocument, "type" | "config"> & {
    rows: Array<Pick<Baz, "id" | "name" | "version">>
} & QueryDocumentMethods<Q_BazListResult>

/**
 * Everything a Bar could be associated with, which is what the association editor offers. A list to pick
 * from rather than rows to edit, so nothing of it is registered with the working set.
 */
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
