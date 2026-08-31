import { GraphQLQuery, } from "@quinscape/qlive-js";

export type Q_TestResult = {
    queryTestFooDocument : Pick<TestFooDocument,"type" | "config"> & {
        rows : Array<Pick<TestFoo,"name"> & {
            owner : Pick<TestUser,"login">
        }>
    }
}

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
