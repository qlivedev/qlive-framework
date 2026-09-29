import { FooDocument, Foo, AppUser, FooType } from "../../types";
import { GraphQLQuery, ClientQueryDocument } from "@qlivedev/qlive-ts";

/**
 * The Foos the grid examples list: every scalar field, the owner and the type as the related rows, and the version
 * that editing needs. One query for all of them, each view injecting it with the config it wants.
 */
export type Q_FooListResult = Pick<FooDocument,"type" | "config" | "rowCount"> & {
    rows : Array<Pick<Foo,"id" | "name" | "description" | "num" | "flag" | "created" | "type" | "ownerId" | "version"> & {
        owner : Pick<AppUser,"id" | "login">,
        fooType : FooType
    }>
} & ClientQueryDocument<Q_FooListResult>

export const Q_FooList = new GraphQLQuery<Q_FooListResult>(
    // language=GraphQL
    `query Q_FooList($config: QueryConfig!) {
        queryFooDocument(config: $config) {
            type
            config
            rowCount
            rows {
                id
                name
                description
                num
                flag
                created
                type
                ownerId
                version
                owner {
                    id
                    login
                }
                fooType {
                    name
                    ordinal
                }
            }
        }
    }`
)
