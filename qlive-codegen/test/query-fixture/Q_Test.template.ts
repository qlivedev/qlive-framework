import { GraphQLQuery, } from "@qlivedev/qlive-ts";

export const Q_Test = new GraphQLQuery<any>(
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
