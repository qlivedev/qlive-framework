import { Bar, BarDocument } from "../../types";
import { GraphQLQuery, QueryDocumentMethods } from "@qlivedev/qlive-ts";

/**
 * Bars as a view that only displays them reads them: two fields and no version.
 *
 * Deliberately narrower than the edit view's query, because that is what the live view demonstrates. A
 * subscription asks about the fields its query selected, so somebody else changing a Bar's description --
 * a column nothing here shows -- never becomes a message, while changing its name does.
 */
export type Q_BarNamesResult = Pick<BarDocument,"type" | "config"> & {
    rows : Array<Pick<Bar,"id" | "name">>
} & QueryDocumentMethods<Q_BarNamesResult>

export const Q_BarNames = new GraphQLQuery<Q_BarNamesResult>(
    // language=GraphQL
    `query Q_BarNames($config: QueryConfig!) {
        queryBarDocument(config: $config) {
            type
            config
            rows {
                id
                name
            }
        }
    }`
)
