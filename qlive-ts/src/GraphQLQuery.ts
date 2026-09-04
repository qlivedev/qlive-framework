import graphql, {firstValue, GraphQLParams} from "./util/graphql";
import {type OperationType, type ParsedQuery, parseQuery, type QuerySelection} from "./util/parseQuery";
import {buildConversionMap} from "./util/conversionMap";
import {convertResultFromServer, convertVariablesToServer, QueryConversionMap} from "./converter";
import {QueryDocument} from "./QueryDocument";

const secret = Symbol("GraphQLQuery Secret")

export class GraphQLQuery<T>
{
    query: string;
    /** name of the operation, e.g. "Q_Foo" */
    queryName: string;
    /** kind of operation, "query" or "mutation" */
    operation: OperationType;
    /** top-level selections of the operation with their alias and field name */
    selections: QuerySelection[];

    private readonly parsed: ParsedQuery;
    private map: QueryConversionMap | null;

    constructor(query: string)
    {
        const parsed = parseQuery(query);

        if (!parsed.name)
        {
            // the name identifies the query, e.g. as injection id
            throw new Error("Query or mutation must be named: " + query);
        }

        if (parsed.usesFragments)
        {
            // Refused here rather than when the conversion map is built: whether a
            // document uses fragments is known without the schema, so the error can
            // point at the module declaring the query instead of at the view that
            // happened to render first.
            throw new Error("Query " + parsed.name + " uses fragments, which are not supported: " + query);
        }

        this.query = query
        this.queryName = parsed.name
        this.operation = parsed.operation
        this.selections = parsed.selections
        this.parsed = parsed
        this.map = null
    }

    /**
     * The conversion map of this query, built from its selections and the schema.
     *
     * Built on first use, not in the constructor: queries are declared at module
     * scope, which is evaluated while the modules are imported -- before startup()
     * has fetched the config the schema comes from.
     */
    get conversionMap(): QueryConversionMap
    {
        if (!this.map)
        {
            this.map = buildConversionMap(this.parsed)
        }
        return this.map
    }

    /**
     * Returns the top-level selection with the given result key, that is the
     * alias if the field was aliased, otherwise the field name.
     *
     * @param key   result key
     *
     * @returns selection or null
     */
    selection(key: string): QuerySelection | null
    {
        return this.selections.find(s => s.key === key) || null
    }

    /**
     * Registers this query with the given result
     * @param result    GraphQL result object to attach this query to
     */
    register(result: T): void
    {
        // The query is stashed on the result under a private symbol; T is
        // opaque here, so the symbol index has to be asserted.
        (result as Record<symbol, unknown>)[secret] = this
    }

    /**
     * Returns the GraphQL query registered with the given object
     * @param o     former GraphQL result with an attached query
     *
     * @returns query attached to object or null
     */
    static access<T>(o: T): GraphQLQuery<T> | null
    {
        return (o as Record<symbol, unknown>)[secret] as GraphQLQuery<T> || null
    }

    execute(params: GraphQLParams): Promise<T>
    {
        const map = this.conversionMap;

        return graphql<any>(this, convertVariablesToServer(params, map))
            .then(data => {

                const result = firstValue(convertResultFromServer(data, map)) as T;

                if (result instanceof QueryDocument)
                {
                    // gives the document the query it came from, which is what
                    // update() re-executes -- inject() does the same for its value
                    this.register(result)
                }

                return result;
            });
    }
}
