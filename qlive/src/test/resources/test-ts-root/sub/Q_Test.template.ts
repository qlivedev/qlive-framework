import {GraphQLQuery,} from "@quinscape/qlive-js";

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
