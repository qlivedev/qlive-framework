import { AppUserDocument, AppUser } from "../types";
import { GraphQLQuery, ClientQueryDocument } from "@qlivedev/qlive-ts";

/**
 * The users a Foo can belong to, as a catalog: the few rows a filter or a foreign key select offers, loaded
 * whole. Their key and the name field that labels them, nothing else.
 */
export type Q_OwnerCatalogResult = Pick<AppUserDocument,"type"> & {
    rows : Array<Pick<AppUser,"id" | "login">>
} & ClientQueryDocument<Q_OwnerCatalogResult>

export const Q_OwnerCatalog = new GraphQLQuery<Q_OwnerCatalogResult>(
    // language=GraphQL
    `query Q_OwnerCatalog($config: QueryConfig!) {
        queryAppUserDocument(config: $config) {
            type
            rows {
                id
                login
            }
        }
    }`
)
