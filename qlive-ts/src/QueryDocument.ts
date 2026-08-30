import {Condition, FieldExpression} from "./FilterDSL";
import {GraphQLQuery} from "./GraphQLQuery";


interface QueryConfig
{
    condition: Condition;
    offset: number;
    pageSize: number;
    sortFields: FieldExpression[];
}

export interface QueryDocumentMethods<T>
{
    update(newConfig: QueryConfig): Promise<QueryDocument<T>>
}

export class QueryDocument<T> implements QueryDocumentMethods<T>
{
    type: string = null
    config: QueryConfig = null;
    rows: T[] = null
    rowCount: number = 0

    constructor(type: string, config: QueryConfig, rows: T[], rowCount: number)
    {
        this.type = type;
        this.config = config;
        this.rows = rows;
        this.rowCount = rowCount;
    }

    update(newConfig: QueryConfig): Promise<QueryDocument<T>>
    {
        const query: GraphQLQuery<QueryDocument<T>> = GraphQLQuery.access(this);
        return query.execute({config: newConfig})
            .then(
                queryDocument => {
                    query.register(queryDocument)
                    return queryDocument;
                })
    }
}
