import {FieldExpression, FilterExpression} from "./FilterDSL";
import {GraphQLQuery} from "./GraphQLQuery";


export interface QueryConfig
{
    // Data only - serialised into the GraphQL query, never called on.
    // Accepts both styles: fluent (a.and(b)) and functional (and(a, b)),
    // plus null for "no filter".
    condition: FilterExpression | null;
    offset: number;
    pageSize: number;
    sortFields: FieldExpression[];
}

/**
 * What a query document can do on top of holding its data.
 *
 * Every document the server sends becomes a QueryDocument instance, so the generated
 * result type of a query selecting one mixes this in: the methods are part of the type
 * the same way they are part of the value.
 *
 * @typeParam D    document type update() resolves to. That is the type this interface is
 *                 mixed into, so an updated document keeps the exact selection of the one
 *                 it was updated from and can be updated again.
 */
export interface QueryDocumentMethods<D>
{
    update(newConfig: QueryConfig): Promise<D>
}

export class QueryDocument<T> implements QueryDocumentMethods<QueryDocument<T>>
{
    type: string;
    config: QueryConfig;
    rows: T[];
    rowCount: number;

    constructor(type: string, config: QueryConfig, rows: T[], rowCount: number)
    {
        this.type = type;
        this.config = config;
        this.rows = rows;
        this.rowCount = rowCount;
    }

    async update(newConfig: QueryConfig): Promise<QueryDocument<T>>
    {
        const query = GraphQLQuery.access<QueryDocument<T>>(this);
        if (!query)
        {
            // Previously this threw an unhelpful TypeError one line further on.
            throw new Error("QueryDocument has no GraphQLQuery registered - it was not created by executing a query");
        }
        return query.execute({config: newConfig});
    }
}
