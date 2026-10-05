import {v4 as uuid} from "uuid";
import {GraphQLQuery} from "../GraphQLQuery";
import {GraphQLField} from "../GraphQLSchema";
import {DocumentOrSnapshot, documentOf} from "../QueryDocument";
import {findType, LIST, objectFields, unwrapAll, unwrapNonNull} from "../type-utils";
import {HeldRows, RowVisit, walkRows} from "../util/rows";
import {scalarEqual} from "../util/scalar";
import {
    createAccessor,
    MergeAccessor,
    MergeEntity,
    MergeHost,
    MergeView,
    Resolution,
    valueOf
} from "./MergeAccessor";
import {mergeWorkingSet} from "./mergeWorkingSet";
import * as MergeMeta from "./meta";
import {EntityChange, EntityDeletion, FieldChange, LinkChange, MergeConflict, MergeResult} from "./types";

/**
 * Marks a draft and carries the entity behind it, so that a draft can be handed back to the working set
 * that made it. A symbol rather than a property: it must not collide with a field of the row and must not
 * survive a spread into a plain object.
 */
const DRAFT = Symbol("QLive WorkingSet draft")

/**
 * Carries the working set a draft belongs to, which is how useMerge() finds one without being handed it.
 * The same reasons as DRAFT: a symbol collides with no field and does not survive a spread.
 */
const SET = Symbol("QLive WorkingSet")

/**
 * What a view renders: the state of one working set at one point in time, plus what moves it on.
 *
 * The working set itself is mutated in place -- it has to be, or the subscription a view holds would point
 * at a stale object. This is the opposite: a fresh object whenever anything changed and the same object as
 * long as nothing did, which is what makes an edit visible to React.
 */
export type WorkingSetSnapshot = {

    /**
     * true while the working set holds anything unsaved: a changed field, a new row, a row marked deleted.
     */
    dirty: boolean

    /**
     * One entry per row the last merge could not write. Empty until a merge comes back with conflicts, and
     * empty again once one lands.
     */
    conflicts: MergeConflict[]

    /**
     * Which values a draft read returns: the user's own edits, what is in the database, or the two folded
     * together. "merged" until something sets it otherwise.
     */
    view: MergeView

    merge: () => Promise<MergeResult>
    undo: () => void
    clear: () => void
    setView: (view: MergeView) => void
}

/**
 * What somebody else's write left in the database, as the working set takes it.
 *
 * An input to the store rather than the shape a merge response happens to have: a field's state is the base
 * it was registered with, the change the user made, and the value that is stored, and a failed merge is
 * merely today's only source of the third. A push message saying a row changed is the next one, and
 * nothing below this has to change for it.
 */
export type StoredState = {
    type: string
    id: string

    /**
     * The version the row stands at now, which is what a second attempt is written against. Left out where
     * the sender does not know it, and null is not that -- an unversioned type has none.
     */
    version?: string | null

    /**
     * true where the row is gone. Nothing to merge into, so no fields come with it.
     */
    deleted?: boolean

    /**
     * The stored values by field name, in the live form of their type. Only the fields that changed: what
     * is not named here is what the row was read with.
     */
    fields?: Record<string, unknown>
}

/**
 * Options a working set is made with. Everything a *type* decides about merging is type meta data and is
 * declared once, in the application's MergeMetadataProvider; what is in here is the other half, which is
 * about this caller.
 */
export type WorkingSetOptions = {

    /**
     * Whether a conflict comes back carrying both values per field: the user's and the one in the database.
     *
     * false where nobody is going to be shown a conflict, e.g. a working set a background job submits.
     * Nothing else about the merge changes -- the conflict is still detected, the write still fails, and
     * the fields that clashed are still named and marked; what a caller with nobody in front of it declines
     * is a copy of the other user's values it has no use for.
     *
     * The type has to agree, and it is the type that decides: values travel where this is true and the
     * application declared MergeMetadataProvider#resolveConflicts for the type. Setting it here can only
     * narrow that, never widen it. Defaults to true, a working set being the thing a form edits through.
     */
    conflictValues?: boolean
}

/**
 * One entity the working set knows about, and everything that has happened to it.
 */
type Entity = {
    type: string
    id: string

    /** the version the row was read at, and the base its write is held to. Null for an unversioned type. */
    version: string | null

    isNew: boolean
    deleted: boolean

    /**
     * what the row was registered with and what a change is a change against: its scalar values, plus the
     * rows of every many-to-many field below it, which is the base the associations are diffed against
     */
    base: Map<string, unknown>

    /** what the user set, by field name: scalar values, and whole arrays for a many-to-many field */
    changes: Map<string, unknown>

    /**
     * what the database holds now, for the fields somebody else's write changed. Empty until a merge
     * comes back with a conflict, which is today's only way to hear about one
     */
    stored: Map<string, unknown>

    /** what the user decided about a field both writes changed */
    resolutions: Map<string, Resolution>

    /** true where the row is not in the database any more, somebody else having deleted it */
    gone: boolean

    /**
     * why this row's own fields cannot be written, where they cannot: a row of a versioned type registered
     * without its version has no base to hold that write to, and null everywhere else. Its associations are
     * another matter -- those are pairs, held to no version at all
     */
    unversioned: string | null

    /** the row itself, or the object a created entity stands on */
    target: Record<string, any>

    draft: any
}


/**
 * The rows an application is editing, the changes it has made to them, and the one call that writes them.
 *
 * A working set is a store like a query document, read through useWorkingSet(): it is mutated in place and
 * hands out a fresh snapshot whenever it changes. Nothing in here is React, which is what lets a form
 * library -- or a form written by hand -- drive it.
 *
 * The rows come from queries the application already ran. register() takes the document, walks it, and
 * remembers what every row looked like and which version it was read at; edit() hands out a draft that
 * records what changes; merge() writes the lot in one transaction.
 */
export class WorkingSet
{
    private readonly conflictValues: boolean;

    /** documents the rows came from, kept so that a merge that landed can leave them holding fresh rows */
    private documents: DocumentOrSnapshot[];

    /** every entity, by type and id */
    private entities: Map<string, Entity>;

    /** which entity a registered row belongs to, which is how edit() recognizes a row it was handed */
    private rows: WeakMap<object, string>;

    private conflicts: MergeConflict[];

    private subscribers: (() => void)[];

    private snapshot: WorkingSetSnapshot | null;

    /** which values a read returns, for every draft and every accessor at once */
    private viewFlag: MergeView;

    /** the accessor per entity, dropped whenever anything changes, the way the snapshot is */
    private accessors: Map<string, MergeAccessor>;

    /** what an accessor reaches the store through, built once because it never varies */
    private readonly host: MergeHost;

    /**
     * true while rows have been registered that no subscriber was told about: a walk from a draft read in a
     * render must not notify, and register() makes up for it
     */
    private unannounced: boolean;


    constructor(options: WorkingSetOptions = {})
    {
        this.conflictValues = options.conflictValues !== false
        this.documents = []
        this.entities = new Map()
        this.rows = new WeakMap()
        this.conflicts = []
        this.subscribers = []
        this.snapshot = null
        this.viewFlag = "merged"
        this.accessors = new Map()
        this.unannounced = false

        this.host = {
            view: () => this.viewFlag,
            resolve: (entity, field, choice) => this.resolveField(entity as Entity, field, choice),
            resolveWith: (entity, field, value) => this.resolveFieldWith(entity as Entity, field, value),
            accessor: row => this.accessor(row)
        }
    }


    /**
     * Registers every row of the given query document, and the rows below them.
     *
     * What it takes from a row is the version it was read at -- the base every write of it is held to --
     * and a copy of its scalar values, which is what a change is a change against. Anything with an id is
     * an entity, whatever type it is and however deep it sits, so registering the document a view renders
     * registers everything that view can edit.
     *
     * A row of a versioned type that came without its version is registered like any other and refuses the
     * write that would need one, naming the query that read it. Registering walks everything a query
     * selected, most of which a view only displays, so the query that reads a lookup table for a dropdown is
     * not the place to insist -- and the field somebody does try to change still fails long before a merge
     * could lose an update.
     *
     * Registering a document again takes the rows it holds now, after a page turn say. Rows new to the
     * working set are a change like any other and reach the subscribers, which is what moves a watch on to
     * them -- also where edit() met them first and bound them without telling anyone.
     *
     * @param document      query document, or the snapshot a view holds of one
     */
    register(document: DocumentOrSnapshot): void
    {
        // The live document where the caller handed a snapshot of one. A view holds snapshots and a
        // snapshot is a still: its rows are the array the document held when it was taken, so a working
        // set that kept one would be looking at the rows of a page that has since been turned.
        const live = documentOf(document) ?? document

        if (!this.documents.includes(live))
        {
            this.documents.push(live)
        }

        this.walk(live)

        if (this.unannounced)
        {
            this.notify()
        }
    }


    /**
     * Returns the draft of the given row: the same row with every change made to it so far, and writes to
     * it recorded rather than applied.
     *
     * ```ts
     * const bar = ws.edit(row)
     * bar.name = "New name"
     * bar.bazes = [...bar.bazes, baz]
     * ```
     *
     * A many-to-many field is set like any other field and means something else: it says which rows this one
     * is associated with, and the merge turns the difference into associations gained and lost.
     *
     * One draft per row, so two components editing the same row edit the same draft. A draft is not the
     * row -- `draft !== row` -- and it is read rather than kept: hold the row, call this on every render.
     * Handing a draft back in returns it unchanged, so calling this twice is free.
     *
     * @param row       row of a registered document, or a draft of one
     *
     * @throws if the row belongs to no entity of this working set
     */
    edit<T extends object>(row: T): T
    {
        const entity = this.entityOf(row)

        if (!entity.draft)
        {
            entity.draft = new Proxy(entity.target, this.draftHandler(entity))
        }

        return entity.draft
    }


    /**
     * Adds a row that does not exist yet and returns its draft.
     *
     * The id is generated here rather than by the database, so that new rows can refer to each other before
     * the server has seen any of them -- a new Bar and a new Baz associated with it go over in one merge.
     *
     * @param type      GraphQL type name
     * @param values    field values the row starts with
     *
     * @returns the draft of the new row
     */
    create<T extends object>(type: string, values: Partial<T> = {}): T
    {
        const id = typeof (values as any).id === "string" ? (values as any).id : uuid()

        const entity: Entity = {
            type,
            id,
            version: null,
            isNew: true,
            deleted: false,
            base: new Map(),
            changes: new Map(),
            stored: new Map(),
            resolutions: new Map(),
            gone: false,
            unversioned: null,
            target: {id},
            draft: null
        }

        this.entities.set(key(type, id), entity)
        this.rows.set(entity.target, key(type, id))

        for (const [name, value] of Object.entries(values))
        {
            if (name !== "id")
            {
                this.record(entity, name, value)
            }
        }

        this.notify()

        return this.edit(entity.target) as unknown as T
    }


    /**
     * The drafts of the rows of the given type that were created here and not saved yet, oldest first.
     *
     * What a list shows above the rows its query returned: a created row is in no query result until a merge
     * wrote it.
     *
     * @param type      GraphQL type name
     */
    created<T extends object = any>(type: string): T[]
    {
        return [...this.entities.values()]
            .filter(entity => entity.isNew && entity.type === type)
            .map(entity => this.edit(entity.target) as unknown as T)
    }


    /**
     * Marks the given row for deletion. A row that was only ever created here is dropped instead: there is
     * nothing to delete, and nothing to tell the server about.
     *
     * @param row       row of a registered document, or a draft of one
     */
    delete(row: object): void
    {
        const entity = this.editable(row)

        if (entity.isNew)
        {
            this.entities.delete(key(entity.type, entity.id))
        }
        else
        {
            entity.deleted = true
        }

        this.notify()
    }


    /**
     * Returns the current values of the given draft as a plain object -- what the row would look like with
     * every change applied.
     *
     * The way out of the working set, for anything that wants a value rather than a draft: a component
     * that keeps its own copy, a payload for something else, a comparison.
     *
     * @param row       row of a registered document, or a draft of one
     */
    raw<T extends object>(row: T): T
    {
        const entity = this.entityOf(row)
        const out: Record<string, any> = {...entity.target}

        // the merged values whatever the view flag says, that flag being about what a form shows and this
        // being about what the row is
        for (const name of [...entity.changes.keys(), ...entity.stored.keys()])
        {
            out[name] = valueOf(entity, name, "merged")
        }

        return out as unknown as T
    }


    /**
     * The merge state of one row: what has happened to each of its fields, and the way to a decision about
     * one two writes changed.
     *
     * The plain call under useMerge(), for anything that is not a React component -- a form library binding
     * to it, a test, a headless check of whether anything is left to decide.
     *
     * @param row       row of a registered document, or a draft of one
     *
     * @throws if the row belongs to no entity of this working set
     */
    accessor(row: object): MergeAccessor
    {
        const entity = this.entityOf(row)
        const id = key(entity.type, entity.id)
        const found = this.accessors.get(id)

        if (found)
        {
            return found
        }

        const made = createAccessor(this.host, entity)
        this.accessors.set(id, made)

        return made
    }


    /**
     * Which values a draft read returns.
     */
    get view(): MergeView
    {
        return this.viewFlag
    }


    /**
     * Switches every draft of this working set over to another view at once.
     *
     * The form does not change and no input has to know: a read goes through the draft, so this re-renders
     * the same form against the user's own values, against what is in the database, or against the two
     * folded together.
     *
     * @param view      which values to return
     */
    setView = (view: MergeView): void =>
    {
        if (view !== this.viewFlag)
        {
            this.viewFlag = view
            this.notify()
        }
    }


    /**
     * What this working set is holding: its rows by type, and the fields it registered of them.
     *
     * The fields are what the queries selected, which is the closest thing to "what the form binds" that
     * a working set can know -- it hands out drafts and is never told which of their fields an input was
     * put on. What it holds moves as rows are registered, created and dropped, so a caller that turned
     * this into a standing subscription re-reads it whenever the working set changes.
     *
     * @returns one entry per type held, in no particular order
     */
    held(): HeldRows[]
    {
        const held = new Map<string, HeldRows>()

        for (const entity of this.entities.values())
        {
            let entry = held.get(entity.type)

            if (!entry)
            {
                entry = {type: entity.type, ids: new Set(), fields: new Set()}
                held.set(entity.type, entry)
            }

            entry.ids.add(entity.id)
            entity.base.forEach((_, name) => entry!.fields.add(name))
        }

        return [...held.values()]
    }


    /**
     * Takes what somebody else's write left in the database.
     *
     * The fields it names become the values a "stored" read returns and the ones a merged read takes where
     * the user has no opinion of their own; a field both writes changed becomes a conflict for the user to
     * decide. A row this working set does not hold is not an error -- with push, most of what arrives is
     * about rows nobody here is editing.
     *
     * merge() calls this for every conflict that came back, which is today's only caller. It is public
     * because the second one is a push message and nothing about it would differ.
     *
     * @param state     the row, the version it stands at, and the fields that changed
     */
    storedState(state: StoredState): void
    {
        const entity = this.entities.get(key(state.type, state.id))

        if (!entity)
        {
            return
        }

        if (state.version)
        {
            // The base moves to what is in the database, which is what makes a second save possible at all.
            // Every conflict stands resolved as the user's own value until they say otherwise -- the person
            // present typed it on purpose, and the one who did not is not here to argue.
            entity.version = state.version
        }

        if (state.deleted)
        {
            entity.gone = true
        }

        for (const [name, value] of Object.entries(state.fields ?? {}))
        {
            entity.stored.set(name, value)
        }

        this.notify()
    }


    /**
     * true while the working set holds anything unsaved.
     */
    get dirty(): boolean
    {
        for (const entity of this.entities.values())
        {
            if (entity.isNew || entity.deleted || pending(entity).length > 0)
            {
                return true
            }
        }

        return false
    }


    /**
     * Takes every change back, leaving the rows as they were registered. Conflicts go with them, and so do
     * the decisions taken about them: they are all about a write that no longer exists.
     *
     * What stays is what the working set was told about the database -- the fields somebody else changed
     * are still changed, and a form still shows them as such. That is knowledge rather than unsaved work, and
     * throwing it away would only mean showing the user values that are no longer there.
     */
    undo = (): void =>
    {
        for (const [id, entity] of [...this.entities])
        {
            if (entity.isNew)
            {
                this.entities.delete(id)
            }
            else
            {
                entity.changes.clear()
                entity.resolutions.clear()
                entity.deleted = false
            }
        }

        this.conflicts = []
        this.notify()
    }


    /**
     * Drops everything, the registered documents included. What undo() is to the changes, this is to the
     * whole working set -- after it, nothing is being edited.
     */
    clear = (): void =>
    {
        this.documents = []
        this.entities = new Map()
        this.rows = new WeakMap()
        this.conflicts = []
        this.viewFlag = "merged"
        this.notify()
    }


    /**
     * Writes everything the working set holds: every change, association and deletion in one transaction, or
     * none of them.
     *
     * Done, and the changes are gone and the registered documents run their query again -- a version left
     * standing in a document that stayed on screen would fail the *next* edit, so refreshing is part of a
     * merge that landed rather than the application's chore.
     *
     * Not done, and nothing was written. The conflicts say which rows stood in the way and, where the type
     * and this working set both allow it, both values per field. The user's changes are all still here,
     * and every conflicted row now stands against the version that is in the database -- so saving again
     * writes the user's values over the other ones. That second save is deliberately theirs to make: a
     * working set never re-sends by itself.
     *
     * @returns what came of it
     */
    merge = async (): Promise<MergeResult> =>
    {
        const changes: EntityChange[] = []
        const links: LinkChange[] = []
        const deletions: EntityDeletion[] = []

        for (const entity of this.entities.values())
        {
            if (entity.deleted)
            {
                deletions.push({type: entity.type, id: entity.id, version: entity.version})
                continue
            }

            const fields = this.fieldChanges(entity)

            if (entity.isNew || fields.length > 0)
            {
                changes.push({
                    type: entity.type,
                    id: entity.id,
                    version: entity.version,
                    new: entity.isNew,
                    changes: fields
                })
            }

            links.push(...linkChanges(entity))
        }

        if (changes.length === 0 && links.length === 0 && deletions.length === 0)
        {
            // Nothing to write: the documents are holding what a merge would have gone and fetched again. A
            // form that saves an untouched row costs a round trip otherwise.
            return {status: "DONE", conflicts: []}
        }

        const result = await mergeWorkingSet(changes, links, deletions, {conflictValues: this.conflictValues})

        if (result.status === "DONE")
        {
            await this.refresh()
        }
        else
        {
            this.conflicts = result.conflicts

            for (const conflict of result.conflicts)
            {
                this.storedState({
                    type: conflict.type,
                    id: conflict.id,
                    version: conflict.storedVersion,
                    deleted: conflict.deleted,
                    fields: storedFields(conflict)
                })
            }
        }

        this.notify()

        return result
    }


    subscribe = (fn: () => void) =>
    {
        this.subscribers.push(fn)

        return () =>
        {
            // replaces the list rather than splicing it, which is what lets a subscriber unsubscribe while
            // notify() is iterating
            this.subscribers = this.subscribers.filter(s => s !== fn)
        }
    }


    getSnapshot = (): WorkingSetSnapshot =>
    {
        if (!this.snapshot)
        {
            this.snapshot = {
                dirty: this.dirty,
                conflicts: this.conflicts,
                view: this.viewFlag,
                merge: this.merge,
                undo: this.undo,
                clear: this.clear,
                setView: this.setView
            }
        }

        return this.snapshot
    }


    /**
     * Drops the current snapshot and tells every subscriber that this working set changed. Anything
     * mutating it calls this, or the change stays invisible.
     */
    private notify(): void
    {
        this.unannounced = false
        this.snapshot = null
        this.accessors = new Map()

        for (const subscriber of this.subscribers)
        {
            subscriber()
        }
    }


    /**
     * Runs the query of every registered document again and registers what comes back, so that the working
     * set and the views are both holding rows at the version the merge just wrote.
     */
    private async refresh(): Promise<void>
    {
        this.entities = new Map()
        this.rows = new WeakMap()
        this.conflicts = []

        // A QueryDocument updates in place and answers with a snapshot, so what comes back is kept only
        // where it is not one -- a caller may have registered something that is neither.
        const refreshed = await Promise.all(this.documents.map(document => document.update({})))

        this.documents = this.documents.map(
            (document, i) => documentOf(document) ? document : refreshed[i]
        )
        this.documents.forEach(document => this.walk(document))
    }


    /**
     * Walks the registered documents again, for a row that belongs to none of them yet.
     *
     * A row is recognised by identity, and a document replaces its row objects whenever its query runs
     * again -- a page turned, a sort changed, the refresh a merge that landed does. The entities survive
     * that, being keyed by type and id, so what the new objects need is to be bound to the ones already
     * here rather than to be registered afresh: registering keeps the first entity for a type and id, so a
     * row that comes back carries the changes the user made to the one it replaces.
     *
     * Only on a miss. The cost is a walk of what is on screen, once, at the moment a form would otherwise
     * have failed.
     */
    private rebind(row: object): Entity | null
    {
        this.documents.forEach(document => this.walk(document))

        return this.known(row)
    }


    /**
     * Registers every row of one document.
     */
    private walk(document: DocumentOrSnapshot): void
    {
        const query = GraphQLQuery.access(document as any)
        const source = query ? `query "${query.queryName}"` : "the query the rows came from"

        walkRows(document.rows, document.type, visited => this.registerRow(visited, source))
    }


    /**
     * Registers one row the walk visited.
     *
     * A row without an id is no entity -- there is nothing to name it by and nothing to hang a change on --
     * which the walk leaves to this to decide, having visited the rows below it either way.
     */
    private registerRow(visited: RowVisit, source: string): void
    {
        const {row, type, values, relations} = visited

        const base = new Map<string, unknown>(values)

        for (const relation of relations)
        {
            if (relation.list && MergeMeta.manyToManyField(type, relation.field))
            {
                // the associations as they stand, which is what a write to the field is diffed against.
                // A copy of the array and not the array: the one the row holds is the view's to render
                // and is free to be replaced.
                base.set(relation.field, [...relation.rows])
            }
        }

        const id = row.id
        if (typeof id !== "string" || id.length === 0)
        {
            return
        }

        const [version, unversioned] = versionOf(type, id, base, source)

        this.rows.set(row, key(type, id))

        if (this.entities.has(key(type, id)))
        {
            // the same row reached twice, through two documents or through a relation from both sides.
            // The first registration is the one the changes are against, so it stays.
            return
        }

        this.unannounced = true
        this.entities.set(key(type, id), {
            type,
            id,
            version,
            isNew: false,
            deleted: false,
            base,
            changes: new Map(),
            stored: new Map(),
            resolutions: new Map(),
            gone: false,
            unversioned,
            target: row,
            draft: null
        })
    }


    /**
     * The entity the given row or draft belongs to.
     *
     * @throws if it belongs to none of this working set's
     */
    private entityOf(row: object): Entity
    {
        const entity = this.known(row) ?? this.rebind(row)

        if (entity)
        {
            return entity
        }

        const drafted: Entity | undefined = (row as any)[DRAFT]

        throw new Error(
            drafted
                ? `${drafted.type} ${drafted.id} is a draft of another working set, or of one this one no ` +
                `longer holds.`
                : "Not a row of this working set: " + JSON.stringify(row) + ". Rows come from a document " +
                "register() walked, or from create()."
        )
    }


    /**
     * The entity the given row or draft belongs to, where the row itself may be written.
     *
     * @throws if it belongs to none of this working set's, or if it is a versioned row that was read
     *         without its version
     */
    private editable(row: object): Entity
    {
        const entity = this.entityOf(row)

        if (entity.unversioned)
        {
            throw new Error(entity.unversioned)
        }

        return entity
    }


    /**
     * The entity the given value belongs to, or null where it belongs to none -- which is a question rather
     * than a mistake for anything that may or may not be one, such as a row the working set has to rebind.
     */
    private known(row: any): Entity | null
    {
        if (!row || typeof row !== "object")
        {
            return null
        }

        const drafted: Entity | undefined = row[DRAFT]

        if (drafted)
        {
            return this.entities.get(key(drafted.type, drafted.id)) === drafted ? drafted : null
        }

        const found = this.rows.get(row)

        return found ? this.entities.get(found) ?? null : null
    }


    /**
     * Reads and writes through a draft. A read comes out of the change map where there is one and off the
     * row otherwise; a write goes into the change map and notifies.
     */
    private draftHandler(entity: Entity): ProxyHandler<any>
    {
        return {
            get: (target, name, receiver) =>
            {
                if (name === DRAFT)
                {
                    return entity
                }

                if (name === SET)
                {
                    return this
                }

                return typeof name === "string" && (entity.changes.has(name) || entity.stored.has(name))
                    ? valueOf(entity, name, this.viewFlag)
                    : Reflect.get(target, name, receiver)
            },

            // A row read from a query has what the query selected, a new row has every field of its type --
            // what nobody set yet reads as undefined -- so that a list checking a column's field against the
            // row finds it on both.
            has: (target, name) =>
                Reflect.has(target, name) ||
                typeof name === "string" && (
                    entity.changes.has(name) ||
                    entity.stored.has(name) ||
                    entity.isNew && objectFields(entity.type).some(field => field.name === name)
                ),

            set: (target, name, value) =>
            {
                if (typeof name !== "string")
                {
                    throw new Error(`Cannot change ${entity.type} through a symbol.`)
                }

                this.change(entity, name, value)

                return true
            }
        }
    }


    /**
     * Records one field of one entity as changed and tells the subscribers.
     */
    private change(entity: Entity, name: string, value: unknown): void
    {
        this.record(entity, name, value)
        this.notify()
    }


    /**
     * Records one field of one entity as changed, or takes the change back where the value is what the
     * database holds -- a field the user typed over and then typed back is not a change, and a row whose
     * every change came back is not dirty.
     *
     * Tells nobody, which is what lets a caller making several changes at once notify only when it is done.
     */
    private record(entity: Entity, name: string, value: unknown): void
    {
        if (name === "id" || name === MergeMeta.VERSION)
        {
            throw new Error(
                `Cannot change ${entity.type}.${name}. It is what names the row and what the write is held ` +
                `to, and both are the working set's to say.`
            )
        }

        const manyToMany = MergeMeta.manyToManyField(entity.type, name)

        if (manyToMany)
        {
            this.recordLinks(entity, manyToMany, value)
            return
        }

        if (unwrapAll(fieldOf(entity.type, name).type).kind === "OBJECT")
        {
            throw new Error(`Cannot change ${entity.type}.${name}: it is not a scalar field.`)
        }

        if (entity.unversioned)
        {
            // Here rather than at edit(), because this is the one write the version is the base for. The
            // many-to-many fields above went out already: an association is a pair and held to no version,
            // so a view that only edits associations never needs this row's.
            throw new Error(entity.unversioned)
        }

        const next = value === undefined ? null : value

        // what "no change" means is what the database holds, so a field somebody else changed is compared
        // against their value rather than against the one this row was read with
        const known = entity.stored.has(name) ? entity.stored : entity.base

        if (known.has(name) && scalarEqual(known.get(name), next))
        {
            entity.changes.delete(name)
        }
        else
        {
            entity.changes.set(name, next)
        }

        // a value typed over a clash is a new value rather than a choice between the two that clashed, so
        // the field goes back to being one nobody has decided about
        entity.resolutions.delete(name)
    }


    /**
     * Records what the user decided about a field both writes changed.
     *
     * Nothing is thrown away either way: choosing "stored" holds the user's own value back rather than
     * dropping it, which is what lets them change their mind without typing it again.
     */
    private resolveField(entity: Entity, name: string, choice: Resolution): void
    {
        if (!entity.stored.has(name))
        {
            throw new Error(
                `Nothing to decide about ${entity.type}.${name}: nobody else wrote it. A field is resolved ` +
                `when two writes changed it, which is what a conflict says.`
            )
        }

        entity.resolutions.set(name, choice)
        this.notify()
    }


    /**
     * Records a third value as the user's decision -- neither what they typed nor what is stored, which is
     * what a description somebody merged by hand is.
     */
    private resolveFieldWith(entity: Entity, name: string, value: unknown): void
    {
        this.record(entity, name, value)
        entity.resolutions.set(name, "mine")
        this.notify()
    }


    /**
     * Records a whole many-to-many field as the rows the row is to be associated with.
     *
     * The field is set rather than changed element by element -- `bar.bazes = [...bar.bazes, baz]` or the same
     * with a filter -- and what is kept is the array, not a diff. The diff is made at merge time against the
     * rows the field was registered with, so an association taken away and put back is no change at all and
     * costs the merge nothing.
     */
    private recordLinks(entity: Entity, field: MergeMeta.ManyToManyField, value: unknown): void
    {
        if (!field.writable)
        {
            throw new Error(
                `Cannot change ${entity.type}.${field.field}: a ${field.linkType} needs values of its own besides ` +
                `the two rows it associates, so the ${field.linkType} rows are what to create and delete.`
            )
        }

        if (!Array.isArray(value))
        {
            throw new Error(
                `Cannot set ${entity.type}.${field.field} to something that is not an array. It holds the ` +
                `${field.targetType} rows the ${entity.type} is associated with, and it is set to the ones it is ` +
                `to have.`
            )
        }

        if (!entity.isNew && !entity.base.has(field.field))
        {
            throw new Error(
                `Cannot change ${entity.type}.${field.field}: the query the rows came from did not select it, so ` +
                `there is nothing to diff against. Select "${field.field}" with the id of every row in it.`
            )
        }

        const held = targetIds(linkBase(entity, field), field)
        const wanted = targetIds(value, field)

        if (held.size === wanted.size && [...wanted].every(id => held.has(id)))
        {
            entity.changes.delete(field.field)
        }
        else
        {
            entity.changes.set(field.field, value)
        }
    }


    /**
     * The changes of one entity as the mutation takes them: a field name and the value wrapped in the
     * scalar type the field has, which is what lets one mutation write every type in the domain.
     */
    private fieldChanges(entity: Entity): FieldChange[]
    {
        const changes: FieldChange[] = []

        for (const name of pending(entity))
        {
            if (MergeMeta.manyToManyField(entity.type, name))
            {
                // no field of this row at all: it becomes associations gained and lost, which
                // linkChanges() makes
                continue
            }

            const value = entity.changes.get(name)

            changes.push({
                field: name,
                value: {type: scalarTypeName(entity.type, name), value}
            })
        }

        return changes
    }
}


/**
 * The key one entity is held under. The id alone would do in a database and does not do here: two types can
 * carry the same id, and nothing stops an application generating one.
 */
function key(type: string, id: string): string
{
    return type + "/" + id
}


/**
 * The version a row of the given type was read at, and why it cannot be edited where it cannot: null and
 * null for a type carrying no version at all, a version and null for a row that has one, null and a
 * sentence for a versioned row that came without one.
 *
 * That last case is not refused here. Registration walks everything a query selected and most of it is only
 * displayed, so the query that fills a dropdown is the wrong place to insist on a version. What it gets
 * instead is the sentence thrown the moment somebody does try to write one of the row's own fields, or to
 * delete it -- written here, where the query that read it is still known, and still long before a merge
 * could lose an update.
 */
function versionOf(
    type: string, id: string, base: Map<string, unknown>, source: string
): [string | null, string | null]
{
    if (!MergeMeta.isVersioned(type))
    {
        return [null, null]
    }

    const version = base.get(MergeMeta.VERSION)

    if (typeof version === "string" && version.length > 0)
    {
        return [version, null]
    }

    return [
        null,
        base.has(MergeMeta.VERSION)
            ? `${type} ${id} has no version. Every row of a versioned type gets one when the merge writes ` +
            `it, so a row without one predates the column and has to be given one before it can be edited.`
            : `${type} ${id} was registered without its version. '${type}' is versioned, so the merge writes ` +
            `its rows against the version they were read at -- select "${MergeMeta.VERSION}" in ${source}.`
    ]
}


/**
 * The working set that made the given draft.
 *
 * How useMerge() finds one without being handed it, and the reason a row that is not a draft is a mistake
 * rather than a silent no-op there: nothing else in the row says which working set it belongs to.
 *
 * @throws if the value is not a draft of any working set
 */
export function workingSetOf(row: any): WorkingSet
{
    const set = row && typeof row === "object" ? row[SET] : undefined

    if (!(set instanceof WorkingSet))
    {
        throw new Error(
            "Not a draft: " + JSON.stringify(row) + ". A draft is what ws.edit() returns, and it is what " +
            "knows the working set it belongs to."
        )
    }

    return set
}


/**
 * The fields of one entity a merge would write: what the user changed, minus the ones they decided to
 * leave to the value that is stored.
 */
function pending(entity: Entity): string[]
{
    return [...entity.changes.keys()].filter(name => entity.resolutions.get(name) !== "stored")
}


/**
 * The stored values one conflict carries, by field name.
 *
 * A field whose value was withheld -- a type that did not opt in to resolution, or a caller with nobody to
 * show it to -- is in here as undefined rather than left out: that the field changed is worth marking in
 * the form whether or not there is a value to put next to it.
 */
function storedFields(conflict: MergeConflict): Record<string, unknown>
{
    const fields: Record<string, unknown> = {}

    for (const field of conflict.fields)
    {
        fields[field.field] = field.stored?.value
    }

    return fields
}


/**
 * One field of the given object type.
 */
function fieldOf(type: string, name: string): GraphQLField
{
    const field = objectFields(type).find(f => f.name === name)

    if (!field)
    {
        throw new Error(`Type "${type}" has no field "${name}".`)
    }

    return field
}


/**
 * The scalar type name a value of the given field travels under, in the form a generic scalar names it:
 * "Timestamp", or "[Timestamp]" for a list of them.
 */
function scalarTypeName(type: string, name: string): string
{
    const field = fieldOf(type, name)
    const named = unwrapAll(field.type).name!

    return unwrapNonNull(field.type).kind === LIST ? "[" + named + "]" : named
}


/**
 * One entity's changed many-to-many fields as the associations they gain and lose: a row the field was
 * registered with and no longer holds is an association lost, one it holds and was not registered with is one
 * gained.
 *
 * Nothing here writes the type on the other side. Setting bar.bazes associates the Bar with other Bazes and
 * never changes a Baz, which is what the user of the framework means by setting it.
 */
function linkChanges(entity: Entity): LinkChange[]
{
    const found: LinkChange[] = []

    // pending rather than every change, so that a user who decided to leave the associations to the other
    // write has that decision honored the way it is for a scalar
    for (const name of pending(entity))
    {
        const field = MergeMeta.manyToManyField(entity.type, name)

        if (!field)
        {
            continue
        }

        const held = targetIds(linkBase(entity, field), field)
        const wanted = targetIds(entity.changes.get(name) as any[], field)

        const added = [...wanted].filter(id => !held.has(id))
        const removed = [...held].filter(id => !wanted.has(id))

        if (added.length > 0 || removed.length > 0)
        {
            found.push({type: entity.type, id: entity.id, field: name, added, removed})
        }
    }

    return found
}


/**
 * The rows the given entity's many-to-many field was registered with, which a write to that field is diffed
 * against. Empty for an entity that was created here and therefore has no associations yet.
 */
function linkBase(entity: Entity, field: MergeMeta.ManyToManyField): any[]
{
    return (entity.base.get(field.field) as any[]) ?? []
}


/**
 * The ids of the given rows. A set, because what a many-to-many field says is which rows are associated --
 * naming one of them twice says nothing more than naming it once.
 */
function targetIds(rows: any[], field: MergeMeta.ManyToManyField): Set<string>
{
    return new Set(rows.map(row => targetIdOf(row, field)))
}


/**
 * The id of one row in a many-to-many field: a row a query returned, a draft, or a row created in this
 * working set.
 */
function targetIdOf(row: any, field: MergeMeta.ManyToManyField): string
{
    const id = row?.id

    if (typeof id !== "string" || id.length === 0)
    {
        throw new Error(
            `A row in ${field.sourceType}.${field.field} has no id, so there is no telling which ` +
            `${field.targetType} it is. Select "id" on "${field.field}" in the query the rows came from.`
        )
    }

    return id
}
