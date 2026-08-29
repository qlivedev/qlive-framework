import { GraphQLQuery, } from "@quinscape/qlive-ts";
import {AppUser, Foo, FooDocument, FooType} from "../types";

export type Q_FooResult = {
    xxx : Pick<FooDocument,"type" | "config"> & {
        rows : Array<Pick<Foo,"id" | "name"> & {
            desc? : String,
            owner : Pick<AppUser,"id" | "login">,
            fooType : Pick<FooType,"ordinal"> & {
                id : String
            }
        }>
    }
}

export const Q_Foo = new GraphQLQuery<Q_FooResult>(
    // language=GraphQL
    `query Q_Foo($config: QueryConfig!) {
         xxx: queryFooDocument(config: $config) {
             type
             config
             rows {
                 
                 id
                 name
                 desc: description
                 
                 owner {
                     id
                     login
                 }
                 fooType {
                     id: name
                     ordinal
                 }
             }
         }
    }`
)
