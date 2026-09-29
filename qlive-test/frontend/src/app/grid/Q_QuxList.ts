import { QuxDocument, Qux } from "../../types";
import { GraphQLQuery, ClientQueryDocument } from "@qlivedev/qlive-ts";

/**
 * Every scalar field of Qux, one per scalar type the framework supports.
 */
export type Q_QuxListResult = Pick<QuxDocument,"type" | "config" | "rowCount"> & {
    rows : Array<Pick<Qux,"id" | "name" | "bool" | "byteValue" | "intValue" | "longValue" | "doubleValue" | "bigDecimalValue" | "currencyValue" | "stringValue" | "dateValue" | "timestampValue">>
} & ClientQueryDocument<Q_QuxListResult>

export const Q_QuxList = new GraphQLQuery<Q_QuxListResult>(
    // language=GraphQL
    `query Q_QuxList($config: QueryConfig!) {
        queryQuxDocument(config: $config) {
            type
            config
            rowCount
            rows {
                id
                name
                bool
                byteValue
                intValue
                longValue
                doubleValue
                bigDecimalValue
                currencyValue
                stringValue
                dateValue
                timestampValue
            }
        }
    }`
)
