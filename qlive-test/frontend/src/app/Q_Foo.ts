import { GraphQLQuery, QueryDocumentMethods } from "@qlivedev/qlive-ts";
import {AppUser, Foo, FooDocument, FooType} from "../types";

export type Q_FooResult = Pick<FooDocument,"type" | "config"> & {
    rows : Array<Pick<Foo,"id" | "name" | "description"> & {
        owner : Pick<AppUser,"id" | "login">
    }>
} & QueryDocumentMethods<Q_FooResult>

export const Q_Foo = new GraphQLQuery<Q_FooResult>(
    // language=GraphQL
        `query Q_Foo($config: QueryConfig!) {
        xxx: queryFooDocument(config: $config) {
            type
            config
            rows {

                id
                name
                description

                owner {
                    id
                    login
                }
            }
        }
    }`
)
