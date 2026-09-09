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
 * Describes the relative changes to a pre-existing QueryConfig.
 */
export interface QueryConfigDelta
{
    condition?: FilterExpression | null;
    offset?: number;
    pageSize?: number;
    sortFields?: FieldExpression[];
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
    update(newConfig: QueryConfigDelta): Promise<D>
}

/**
 * What a view actually renders: the state of one query document at one point in time,
 * plus the methods that move it on.
 *
 * The document itself is a store that gets mutated in place -- it has to be, or the
 * subscription a view holds would point at a stale object after every update. What
 * comes out of getSnapshot() is the opposite: a fresh object every time the document
 * changes and the same object as long as it does not. That is what makes an update
 * visible to React.memo, to effect dependencies and to useSyncExternalStore's own
 * change detection.
 */
export interface QueryDocumentSnapshot<T> extends QueryDocumentMethods<QueryDocumentSnapshot<T>>
{
    type: string
    config: QueryConfig
    rows: T[]
    rowCount: number
}

export class QueryDocument<T> implements QueryDocumentMethods<QueryDocumentSnapshot<T>>
{
    type: string;
    config: QueryConfig;
    rows: T[];
    rowCount: number;

    private subscribers: (() => void)[];
    private snapshot: QueryDocumentSnapshot<T> | null;

    constructor(type: string, config: QueryConfig, rows: T[], rowCount: number)
    {
        this.type = type;
        this.config = config;
        this.rows = rows;
        this.rowCount = rowCount;

        this.subscribers = []
        this.snapshot = null
    }

    /**
     * Re-executes the query this document came from with its config changed as given and
     * updates the document in place.
     *
     * An arrow property, not a method: it is handed out on every snapshot, where it is
     * called detached from the document and needs to keep both its "this" and its identity.
     *
     * @param newConfig     config fields to change
     *
     * @returns the snapshot the update produced
     */
    update = async (newConfig: QueryConfigDelta): Promise<QueryDocumentSnapshot<T>> =>
    {
        const query = GraphQLQuery.access<QueryDocument<T>>(this);
        if (!query)
        {
            // Only a document that came out of an execution carries the query update() re-runs.
            throw new Error("QueryDocument has no GraphQLQuery registered - it was not created by executing a query");
        }

        const mergedConfig : QueryConfig = {
            ...this.config,
            ...newConfig,
        }

        const queryDocument = await query.execute({config: mergedConfig});

        this.rows = queryDocument.rows;
        this.config = queryDocument.config;
        this.rowCount = queryDocument.rowCount;

        return this.notify()
    }

    subscribe = (fn: () => void) => {
        this.subscribers.push(fn)

        return () => {
            // replaces the list rather than splicing it, which is what lets a
            // subscriber unsubscribe while notify() is iterating
            this.subscribers = this.subscribers.filter( s => s !== fn)
        }
    }

    getSnapshot = () : QueryDocumentSnapshot<T> =>
    {
        if (!this.snapshot)
        {
            this.snapshot = {
                type: this.type,
                rows: this.rows,
                config: this.config,
                rowCount: this.rowCount,
                update: this.update,
            }
        }

        return this.snapshot
    }

    /**
     * Drops the current snapshot and tells every subscriber that this document changed.
     * Anything mutating the document calls this, or the change stays invisible.
     *
     * @returns the snapshot describing the document as it is now
     */
    private notify = () : QueryDocumentSnapshot<T> =>
    {
        this.snapshot = null

        for (const subscriber of this.subscribers)
        {
            subscriber()
        }

        return this.getSnapshot()
    }
}
