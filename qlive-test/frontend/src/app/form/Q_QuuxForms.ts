import { QuuxDocument } from "../../types";
import { GraphQLQuery, ClientQueryDocument } from "@qlivedev/qlive-ts";

/**
 * The quuxes the invariants example edits, with every field Quux's invariants read: an update sends those it
 * doesn't change along with the ones it does, and the working set can only send what the query selected.
 *
 * The result type below is generated from the query and rewritten whenever the selection changes, so
 * nothing may sit between it and the query -- this note included, which is why it stands above it.
 */
export type Q_QuuxFormsResult = Pick<QuuxDocument,"type" | "config" | "rows"> & ClientQueryDocument<Q_QuuxFormsResult>

export const Q_QuuxForms = new GraphQLQuery<Q_QuuxFormsResult>(
    // language=GraphQL
    `query Q_QuuxForms($config: QueryConfig!) {
        queryQuuxDocument(config: $config) {
            type
            config
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
