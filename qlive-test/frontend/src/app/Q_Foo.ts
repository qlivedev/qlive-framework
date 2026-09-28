import { GraphQLQuery, QueryDocumentMethods } from "@qlivedev/qlive-ts";
import {AppUser, Foo, FooDocument} from "../types";

export type Q_FooResult = Pick<FooDocument,"type" | "config" | "rowCount"> & {
    rows : Array<Pick<Foo,"id" | "name" | "description" | "num" | "flag" | "created" | "type" | "ownerId" | "version"> & {
        owner : Pick<AppUser,"id" | "login">
    }>
} & QueryDocumentMethods<Q_FooResult>

export const Q_Foo = new GraphQLQuery<Q_FooResult>(
    // language=GraphQL
        `query Q_Foo($config: QueryConfig!) {
        xxx: queryFooDocument(config: $config) {
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
            }
        }
    }`
)
