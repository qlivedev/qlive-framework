import config from "../config";
import {and, field, FilterExpression, or, value, values} from "../FilterDSL";
import {maskedFields, maskOf} from "../merge/fieldMask";
import {WorkingSet} from "../merge/WorkingSet";
import {subscribeToTopic} from "../pubsub";
import {QueryDocument} from "../QueryDocument";
import {HeldRows, heldRows} from "../util/rows";

/**
 * The channel every merge publishes to, named as EntityVersionPublisher registers it.
 */
export const ENTITY_VERSION = "EntityVersion"

/**
 * One recorded change to one row, as it arrives: which row changed, which fields it touched, and who did
 * it.
 *
 * The EntityVersion record the merge writes, and nothing more -- no field values travel, which is what
 * makes the message the same size whatever changed and is why what a subscriber can do with one is either
 * mark it or go and read the row.
 */
export interface EntityVersionMessage
{
    /** the version the row stands at now */
    id: string

    entityType: string

    entityId: string

    /** the version the change was made against, null for a row that had none */
    prev: string | null

    /** the fields that changed, as a decimal string: 128 bits is past what a number holds exactly */
    fieldMask: string | null

    /** id of the field layout the mask was written against */
    fieldLayout: string

    /** who made the change */
    ownerId: string | null

    /** when, ISO-8601 */
    created: string
}

/**
 * One row somebody else changed under us, and the fields of it they touched.
 */
export interface RemoteChangedRow
{
    type: string

    id: string

    /** the fields the change touched that this client has a name for, in bit order */
    fields: string[]
}


/**
 * The subscription that hears about changes to the given rows and nothing else.
 *
 * One clause group per type, because a document's rows carry entities of several types and each has its
 * own bit positions, all of it under one "not me". A change to a field nobody here selected never becomes
 * a message, which is the whole point of sending the mask rather than filtering on arrival.
 *
 * @param held      what the store is holding
 *
 * @returns the condition, or null where there is nothing to hear about
 */
function conditionFor(held: HeldRows[]): FilterExpression | null
{
    const perType = held
        .filter(({ids, fields}) => ids.size > 0 && fields.size > 0)
        .map(({type, ids, fields}) => and(
            field("entityType").eq(value(type)),
            field("entityId").in(values("String", ...ids)),
            field("fieldMask").bitAnd(value(maskOf(type, fields).toString())).ne(value(0))
        ))

    if (perType.length === 0)
    {
        return null
    }

    const mine = config().authentication?.id

    // Our own writes come back as messages like any other and are the one thing a form must not be told
    // about: it is already showing what it just wrote.
    return mine ? and(or(...perType), field("ownerId").ne(value(mine))) : or(...perType)
}


/**
 * A stable spelling of what a store holds, which is how "the rows on screen changed" is recognised without
 * comparing conditions.
 */
function keyOf(held: HeldRows[]): string
{
    return held
        .map(({type, ids, fields}) =>
            type + "(" + [...ids].sort().join(",") + ")[" + [...fields].sort().join(",") + "]")
        .sort()
        .join(";")
}


/**
 * Keeps one subscription in step with what a store is holding.
 *
 * What is on screen moves -- a page turns, a sort changes, a row is created -- and a subscription's
 * condition is fixed once it is registered. So a store that changed is subscribed anew and the old
 * registration dropped afterwards, in that order: between the two the client hears a message twice, and
 * the other order would have it hear nothing at all.
 *
 * Not debounced. What this follows is an id set, and an id set turns over on a page change, which is a
 * user action rather than a keystroke.
 */
function watch(read: () => HeldRows[], changed: (row: RemoteChangedRow) => void)
{
    let key: string | null = null
    let unsubscribe: (() => void) | null = null

    const receive = (message: EntityVersionMessage) =>
    {
        changed({
            type: message.entityType,
            id: message.entityId,
            fields: message.fieldMask ? maskedFields(message.entityType, BigInt(message.fieldMask)) : []
        })
    }

    const refresh = () =>
    {
        const held = read()
        const next = keyOf(held)

        if (next === key)
        {
            return
        }
        key = next

        const previous = unsubscribe
        const condition = conditionFor(held)

        unsubscribe = condition
            ? subscribeToTopic<EntityVersionMessage>(ENTITY_VERSION, receive, condition)
            : null

        previous?.()
    }

    const close = () =>
    {
        unsubscribe?.()
        unsubscribe = null
        key = null
    }

    return {refresh, close}
}


/**
 * The watch each working set has, and how many callers are holding it.
 */
const watchers = new WeakMap<WorkingSet, {held: number, close: () => void}>()


/**
 * Hands back the one function that stops the caller watching: idempotent, because a React effect cleanup
 * may run more than once and a caller counted twice would keep a watch alive that nothing is holding.
 */
function release(workingSet: WorkingSet, entry: {held: number, close: () => void}): () => void
{
    let released = false

    return () =>
    {
        if (released)
        {
            return
        }
        released = true

        if (--entry.held === 0)
        {
            watchers.delete(workingSet)
            entry.close()
        }
    }
}


/**
 * Tells the given working set about the writes other people land on the rows it is holding, as they land.
 *
 * The rows are being edited, so a change to one means something on its own: the field takes the status it
 * would have taken at save time, and the user sees it while they are still typing rather than afterwards.
 * No values arrive, which the working set already has a shape for -- a field known to have changed and not
 * known to what reads as the value the row was read with, and carries the same status a conflict does.
 *
 * The version is deliberately left where it stands. Adopting the one that just landed would quietly remove
 * the guard the next save runs into, and the marks would be the only warning the user ever got; leaving it
 * keeps the merge as the authority and makes this the early notice it is meant to be.
 *
 * A row this working set holds is its case and not a document's, even where the document it was registered
 * from is on screen: refetching that document would swap the row objects the drafts stand on. A view that
 * edits watches its working set -- useWorkingSet({watch: true}) -- and a view that only displays watches
 * its document.
 *
 * One subscription per working set, however many callers ask for it: a working set is read by as many
 * components as care to render it, and what arrives is applied to the set rather than handed to a caller,
 * so a second registration would only deliver every message twice. The last caller to stop watching is the
 * one that closes it.
 *
 * @param workingSet    the working set to keep current
 *
 * @returns a function that stops watching
 */
export function watchWorkingSet(workingSet: WorkingSet): () => void
{
    const shared = watchers.get(workingSet)

    if (shared)
    {
        shared.held++

        return release(workingSet, shared)
    }

    const watcher = watch(
        () => workingSet.held(),
        ({type, id, fields}) =>
        {
            const stored: Record<string, unknown> = {}

            // undefined is the value: it says the field changed and that we were not told to what, which
            // is the whole of a mask-only message.
            fields.forEach(name => { stored[name] = undefined })

            workingSet.storedState({type, id, fields: stored})
        }
    )

    const unlisten = workingSet.subscribe(watcher.refresh)
    watcher.refresh()

    const entry = {
        held: 1,
        close: () =>
        {
            unlisten()
            watcher.close()
        }
    }

    watchers.set(workingSet, entry)

    return release(workingSet, entry)
}


/**
 * What has happened under a document since it was last read.
 */
export interface DocumentWatchSnapshot
{
    /**
     * true once somebody else's write touched a row this document is showing, in a field it selected.
     *
     * Whether that is worth telling the user about, refetching for, or ignoring is the application's, and
     * this is the whole of what the framework knows.
     */
    stale: boolean

    /** what somebody else changed, oldest first */
    remoteChanged: RemoteChangedRow[]
}

/**
 * A document's view of what other people are writing: a store like the document itself.
 */
export interface DocumentWatch
{
    subscribe: (fn: () => void) => () => void

    getSnapshot: () => DocumentWatchSnapshot

    /** starts watching, and does nothing to a watch that is watching already */
    open: () => void

    /** forgets what has arrived, for a view that dismissed the notice without running the query again */
    clear: () => void

    /** stops watching, reversibly: open() puts the subscription back */
    close: () => void
}


/**
 * How a watch is to be started.
 */
export interface WatchOptions
{
    /**
     * Whether the watch is watching when it is handed over. Default true, which is what a caller outside
     * React wants: it asked for a watch and gets one that is live.
     *
     * Opening registers a subscription, which is a side effect and so belongs in an effect rather than in
     * a render -- React calls a useState() initializer twice under StrictMode and keeps one of the two
     * results, so a watch that opened where it was created would leave a second, live one that nothing
     * holds and nothing can ever close. useDocumentWatch() therefore constructs the watch closed and opens
     * it from its effect.
     */
    open?: boolean
}

/**
 * Watches the rows of a query document for other people's writes, and reports them.
 *
 * Nothing is applied to the document and nothing can be: the message carries a mask and no values, so what
 * is knowable is that a row on screen is no longer what the database holds. What an application does with
 * that runs from nothing at all, through a notice the user acts on, to running the query again -- and none
 * of those is a decision a framework is in a position to make, so this reports and stops.
 *
 *     const live = useDocumentWatch(bars)
 *     ...
 *     { live.stale && <button onClick={ () => bars.update({}) }>Reload</button> }
 *
 * Running the query again is `update({})`, which the document has already. What arrived is forgotten when
 * the document changes, that being either the refetch or a page turn, and either way rows that are fresh.
 *
 * @param document      the document to watch
 * @param options       how to start it, see WatchOptions
 *
 * @returns the store, which has to be closed when the view holding it goes
 */
export function watchDocument(document: QueryDocument<any>, options: WatchOptions = {}): DocumentWatch
{
    let remoteChanged: RemoteChangedRow[] = []
    let snapshot: DocumentWatchSnapshot | null = null
    let listeners: (() => void)[] = []

    const notify = () =>
    {
        snapshot = null
        listeners.forEach(listener => listener())
    }

    const watcher = watch(
        () => heldRows(document.rows, document.type),
        row =>
        {
            remoteChanged = [...remoteChanged, row]
            notify()
        }
    )

    const clear = () =>
    {
        if (remoteChanged.length > 0)
        {
            remoteChanged = []
            notify()
        }
    }

    // null while closed, which is also how open() knows there is nothing to do.
    let unlisten: (() => void) | null = null

    const open = () =>
    {
        if (unlisten)
        {
            return
        }

        unlisten = document.subscribe(() =>
        {
            // The document moved on: its rows are what the query says they are now, so what we had
            // collected about the ones before them is spent.
            clear()
            watcher.refresh()
        })

        watcher.refresh()
    }

    if (options.open !== false)
    {
        open()
    }

    return {
        open,

        subscribe: fn =>
        {
            listeners.push(fn)

            // replaces the list rather than splicing it, so a listener may leave while notify() iterates
            return () => { listeners = listeners.filter(l => l !== fn) }
        },

        getSnapshot: () =>
        {
            if (!snapshot)
            {
                snapshot = {stale: remoteChanged.length > 0, remoteChanged}
            }

            return snapshot
        },

        clear,

        close: () =>
        {
            unlisten?.()
            unlisten = null
            watcher.close()
        }
    }
}
