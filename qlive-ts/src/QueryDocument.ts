import {FieldExpression, FilterExpression} from "./FilterDSL";
import {GraphQLQuery} from "./GraphQLQuery";

/**
 *  The configuration part of a QueryDocument.
 */
export interface QueryConfig
{
    /**
     * FilterDSL condition or `null` for "Not filtered".
     */
    condition: FilterExpression | null;
    /**
     * Current offset in rows.
     */
    offset: number;
    /**
     * Current pagination size
     */
    pageSize: number;
    /**
     * Array of sort field expression which are either column names with optional `!` prefix to describe descending sort
     * or a complex field expression like the sum of two fields.
     */
    sortFields: FieldExpression[];
}

/**
 * Describes the relative changes to a pre-existing QueryConfig.
 */
export interface QueryConfigDelta
{
    /**
     * FilterDSL condition or `null` for "Not filtered".
     */
    condition?: FilterExpression | null;
    /**
     * Current offset in rows.
     */
    offset?: number;
    /**
     * Current pagination size
     */
    pageSize?: number;
    /**
     * Array of sort field expression which are either column names with optional `!` prefix to describe descending sort
     * or a complex field expression like the sum of two fields.
     */
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
    /**
     * Re-executes the query this document snapshot came from with its config changed as given and
     * updates the document in place.
     *
     * Calling this from a snapshot will update the mutable QueryDocument it came up which in turn will trigger
     * a rerendering of that document, so the returned snapshot is mostly there in case anybody else might need it
     * before that.
     * 
     * @param newConfig     config fields to change
     *
     * @returns the snapshot the update produced
     */
    update(newConfig: QueryConfigDelta): Promise<D>
}

/**
 * Immutable snapshot of a QueryDocument
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
    /**
     * The name of the row type
     */
    type: string;
    /**
     * Current config
     */
    config: QueryConfig;
    /**
     * The current result rows.
     */
    rows: T[];
    /**
     * Total number of available rows.
     */
    rowCount: number;
}

/**
 * A query document as anything holding on to one takes it: the type of its rows, the rows, and the way to
 * run the query again. Both a QueryDocument and the snapshot a view holds of one are this.
 *
 * A view has the snapshot -- useInjection() returns one -- and what has to be held on to is the document
 * behind it, so everything taking this resolves through documentOf().
 *
 * @internal
 */
export interface DocumentOrSnapshot
{
    type: string
    rows: any[]

    update(delta: QueryConfigDelta): Promise<DocumentOrSnapshot>
}

/**
 * Carries the document a snapshot was taken of. A symbol rather than a property: it must not collide with
 * a field of the result and must not survive a spread into a plain object.
 */
const DOCUMENT = Symbol("QLive QueryDocument")

/**
 * QueryDocument or its snapshots are the way you are interacting with injections.
 *
 */
export class QueryDocument<T> implements QueryDocumentMethods<QueryDocumentSnapshot<T>>
{
    /**
     * The name of the row type
     */
    type: string;
    /**
     * Current config
     */
    config: QueryConfig;
    /**
     * The current result rows.
     */
    rows: T[];
    /**
     * Total number of available rows.
     */
    rowCount: number;

    /**
     * Subscriber functions
     * @private
     */
    private subscribers: (() => void)[];

    /**
     * The current snapshot
     * @private
     */
    private snapshot: QueryDocumentSnapshot<T> | null;

    /**
     * Creates a new QueryDocument from raw data.
     *
     * @param type          row type name
     * @param config        current config
     * @param rows          current result rows
     * @param rowCount      total number of rows available
     */
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

    /**
     * Subscribe to this query document for updates
     *
     * @param fn    subscriber function
     * @internal
     */
    subscribe = (fn: () => void) => {
        this.subscribers.push(fn)

        return () => {
            // replaces the list rather than splicing it, which is what lets a
            // subscriber unsubscribe while notify() is iterating
            this.subscribers = this.subscribers.filter( s => s !== fn)
        }
    }

    /**
     * Returns a snapshot of thie query document. The snapshot will only be different if the query document is changed.
     * @internal
     */
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

            // A snapshot is a still of the document as it was, and anything holding one past an update()
            // holds rows the document no longer has. documentOf() is how a holder gets back to the live one.
            Object.defineProperty(this.snapshot, DOCUMENT, {value: this, enumerable: false})
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


/**
 * The document the given value is, or is a snapshot of.
 *
 * A snapshot is what a view holds and what it hands on, and it stops being current the moment the document
 * it came from is updated -- its rows are the array the document held then. Anything that has to stay with
 * the document rather than with one still of it resolves through here.
 *
 * @param value     a query document, a snapshot of one, or anything else
 *
 * @returns the document, or null where the value is neither
 */
export function documentOf(value: unknown): QueryDocument<any> | null
{
    if (value instanceof QueryDocument)
    {
        return value
    }

    const document = value && typeof value === "object" ? (value as any)[DOCUMENT] : null

    return document instanceof QueryDocument ? document : null
}
