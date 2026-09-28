import { TestFooDocument, TestFoo, TestUser } from "../types";
import { GraphQLQuery, ClientQueryDocument } from "@qlivedev/qlive-ts";

export type Q_TestResult = Pick<TestFooDocument,"type" | "config"> & {
    rows : Array<Pick<TestFoo,"name"> & {
        owner : Pick<TestUser,"login">
    }>
} & ClientQueryDocument<Q_TestResult>

export const Q_Test = new GraphQLQuery<Q_TestResult>(
    // language=GraphQL
    `query Q_Test($config: QueryConfig!) {
        queryTestFooDocument(config: $config) {
            type
            config
            rows {
                name
                owner {
                    login
                }
            }
        }
    }`
)
