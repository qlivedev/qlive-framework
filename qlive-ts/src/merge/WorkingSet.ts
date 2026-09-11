import {v4 as uuid} from "uuid";
import {GraphQLQuery} from "../GraphQLQuery";
import {GraphQLField} from "../GraphQLSchema";
import {QueryConfigDelta} from "../QueryDocument";
import {findType, LIST, objectFields, unwrapAll, unwrapNonNull} from "../type-utils";
import {RowVisit, walkRows} from "../util/rows";
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
import {EntityChange, EntityDeletion, FieldChange, MergeConflict, MergeResult} from "./types";

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
 * A query document as a working set uses one: the type of its rows, the rows, and the way to run the query
 * again. Both a QueryDocument and the snapshot a view holds of one are this.
 */
export interface RegisteredDocument
{
    type: string
    rows: any[]

    update(delta: QueryConfigDelta): Promise<RegisteredDocument>
}

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
 * merely today's only source of the third. A push message saying a row moved is the next one, and nothing
 * below this has to change for it.
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
     * The stored values by field name, in the live form of their type. Only the fields that moved: what is
     * not named here is what the row was read with.
     */
    fields?: Record<string, unknown>

    /**
     * Associations known to be in the database, by link field: the ids of the rows on the other side. Only
     * the ones that are known -- an association somebody else made says nothing about the rest of the set,
     * and nothing here claims to be all of it.
     */
    links?: Record<string, string[]>
}

/**
 * Options a working set is made with. Everything a *type* decides about merging is type meta data and is
 * declared once, in the application's MergeMetadataProvider; what is in here is the other half, which is
 * about this caller.
 */
export type WorkingSetOptions = {

    /**
     * false where nobody is going to be shown a conflict, e.g. a working set a background job submits. A
     * conflict then names the fields that clashed and carries no values, there being nobody to choose
     * between them. Defaults to true, a working set being the thing a form edits through.
     */
    resolveConflicts?: boolean
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
     * rows of every link array below it, which is the base the associations are diffed against
     */
    base: Map<string, unknown>

    /** what the user set, by field name: scalar values, and whole arrays for a link field */
    changes: Map<string, unknown>

    /**
     * what the database holds now, for the fields somebody else's write moved. Empty until a merge comes
     * back with a conflict, which is today's only way to hear about one
     */
    stored: Map<string, unknown>

    /** what the user decided about a field both writes changed */
    resolutions: Map<string, Resolution>

    /**
     * per link field, the rows this one is known to be associated with already, whoever made the
     * association. What keeps an insert somebody else got in front of from being sent a second time
     */
    linked: Map<string, Set<string>>

    /** true where the row is not in the database any more, somebody else having deleted it */
    gone: boolean

    /**
     * why this row's own fields cannot be written, where they cannot: a row of a versioned type registered
     * without its version has no base to hold that write to, and null everywhere else. Its associations are
     * another matter -- those are rows of the link type and are held to the versions of those
     */
    unversioned: string | null

    /** the row itself, or the object a created entity stands on */
    target: Record<string, any>

    draft: any
}


/**
 * Where one synthesised link deletion came from: the row whose link array was edited, and which array.
 *
 * A link diff makes rows nobody named, so a conflict about one of them has to be traced back before it can
 * be shown -- the user edited "the associations of this Bar", and that is the only thing a form has a place
 * to mark.
 */
type LinkSource = {
    type: string
    id: string
    field: string

    /** the row on the other side, for a link the diff wanted to insert. Absent for one it wanted to delete */
    target?: string
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
    private readonly resolveConflicts: boolean;

    /** documents the rows came from, kept so that a merge that landed can leave them holding fresh rows */
    private documents: RegisteredDocument[];

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


    constructor(options: WorkingSetOptions = {})
    {
        this.resolveConflicts = options.resolveConflicts !== false
        this.documents = []
        this.entities = new Map()
        this.rows = new WeakMap()
        this.conflicts = []
        this.subscribers = []
        this.snapshot = null
        this.viewFlag = "merged"
        this.accessors = new Map()

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
     * @param document      query document, or the snapshot a view holds of one
     */
    register(document: RegisteredDocument): void
    {
        if (!this.documents.includes(document))
        {
            this.documents.push(document)
        }

        this.walk(document)
    }


    /**
     * Returns the draft of the given row: the same row with every change made to it so far, and writes to
     * it recorded rather than applied.
     *
     * ```ts
     * const bar = ws.edit(row)
     * bar.name = "New name"
     * bar.bazLinks = [...bar.bazLinks, {baz}]
     * ```
     *
     * A link array is set like any other field and means something else: it says which rows this one is
     * associated with, and the merge turns the difference into inserts and deletions of the link type.
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
     * the server has seen any of them -- a new Bar and a new BarLink pointing at it go over in one merge.
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
            linked: new Map(),
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
     * @param state     the row, the version it stands at, and the fields that moved
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

        for (const [name, targets] of Object.entries(state.links ?? {}))
        {
            const known = entity.linked.get(name) ?? new Set<string>()

            targets.forEach(id => known.add(id))
            entity.linked.set(name, known)
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
     * What stays is what the working set was told about the database -- the fields somebody else moved are
     * still moved, and a form still shows them as such. That is knowledge rather than unsaved work, and
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
     * Writes everything the working set holds: every change and every deletion in one transaction, or none
     * of them.
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
        const deletions: EntityDeletion[] = []
        const deleted = new Set<string>()

        // which link array each synthesised link deletion came out of, so that a conflict about a link row
        // can be reported against the field the user actually edited
        const sources = new Map<string, LinkSource>()

        for (const entity of this.entities.values())
        {
            if (entity.deleted)
            {
                deletions.push({type: entity.type, id: entity.id, version: entity.version})
                deleted.add(key(entity.type, entity.id))
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
        }

        // the link diffs after every row, so that a link insert follows the rows it names rather than
        // sitting in front of one of them
        for (const entity of this.entities.values())
        {
            if (!entity.deleted)
            {
                this.diffLinks(entity, changes, deletions, deleted, sources)
            }
        }

        if (changes.length === 0 && deletions.length === 0)
        {
            if (!this.dirty)
            {
                // Nothing to write and nothing that wanted writing: the documents are holding what a merge
                // would have gone and fetched again. A form that saves an untouched row costs a round trip
                // otherwise.
                return {status: "DONE", conflicts: []}
            }

            // Nothing left to write because what was asked for is already true -- an association both
            // people removed. That is a merge that landed, and the documents are stale by exactly the write
            // that made it true, so they are refreshed like after any other.
            await this.refresh()
            this.notify()

            return {status: "DONE", conflicts: []}
        }

        const result = await mergeWorkingSet(changes, deletions, {resolveConflicts: this.resolveConflicts})

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

                const source = sources.get(key(conflict.type, conflict.id))

                if (source)
                {
                    // The link row is not a field of any form, and the array it came out of is. Marked as
                    // moved and not to what: an association somebody else took away says nothing about the
                    // ones they may have added, so the set that is stored is not knowable from here.
                    //
                    // An insert that came back conflicted is one the database already holds -- the only way
                    // a new link row is refused is the constraint on the pair -- so the association it
                    // wanted is recorded as made, and the next merge does not ask for it again.
                    this.storedState({
                        type: source.type,
                        id: source.id,
                        fields: {[source.field]: undefined},
                        links: source.target ? {[source.field]: [source.target]} : undefined
                    })
                }
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

        this.documents = await Promise.all(this.documents.map(document => document.update({})))
        this.documents.forEach(document => this.walk(document))
    }


    /**
     * Registers every row of one document.
     */
    private walk(document: RegisteredDocument): void
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
            if (relation.list && MergeMeta.linkRelation(type, relation.field))
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
            linked: new Map(),
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
        const entity = this.known(row)

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
     * than a mistake for anything that may or may not be one, such as a link the user put in an array.
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

        const relation = MergeMeta.linkRelation(entity.type, name)

        if (relation)
        {
            this.recordLinks(entity, relation, value)
            return
        }

        if (unwrapAll(fieldOf(entity.type, name).type).kind === "OBJECT")
        {
            throw new Error(`Cannot change ${entity.type}.${name}: it is not a scalar field.`)
        }

        if (entity.unversioned)
        {
            // Here rather than at edit(), because this is the one write the version is the base for. The
            // link arrays above went out already: those become rows of the link type, held to the versions
            // of *those*, so a view that only edits associations never needs this row's.
            throw new Error(entity.unversioned)
        }

        const next = value === undefined ? null : value

        // what "no change" means is what the database holds, so a field somebody else moved is compared
        // against their value rather than against the one this row was read with
        const known = entity.stored.has(name) ? entity.stored : entity.base

        if (known.has(name) && sameValue(known.get(name), next))
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
     * Records a whole link array as the associations the row is to have.
     *
     * A link array is set rather than changed field by field -- `bar.bazLinks = [...bar.bazLinks, {baz}]`
     * or the same with a filter -- and what is kept is the array, not a diff. The diff is made at merge
     * time against the array the row was registered with, so an association taken away and put back is no
     * change at all and costs the merge nothing.
     */
    private recordLinks(entity: Entity, relation: MergeMeta.LinkRelation, value: unknown): void
    {
        if (!Array.isArray(value))
        {
            throw new Error(
                `Cannot set ${entity.type}.${relation.field} to something that is not an array. A link ` +
                `array holds ${relation.linkType} rows, and it is set to the ones the row is to have.`
            )
        }

        if (!entity.isNew && !entity.base.has(relation.field))
        {
            throw new Error(
                `Cannot change ${entity.type}.${relation.field}: the query the rows came from did not ` +
                `select it, so there is nothing to diff against and the merge would insert links that are ` +
                `already there. Select "${relation.field}" with the id of every link in it.`
            )
        }

        const held = targetIds(linkBase(entity, relation), relation)
        const wanted = targetIds(value, relation)

        if (held.size === wanted.size && [...wanted].every(id => held.has(id)))
        {
            entity.changes.delete(relation.field)
        }
        else
        {
            entity.changes.set(relation.field, value)
        }
    }


    /**
     * Turns one entity's changed link arrays into the link rows they mean: an association the base had and
     * the array no longer has is a deleted link row, one the array has and the base did not is a new one.
     *
     * Nothing here writes the type on the other side. Editing bar.bazLinks inserts and deletes BarLink rows
     * and never touches Baz, which is what the GraphQL type of the field already says and what the user of
     * the framework means by setting it.
     */
    private diffLinks(
        entity: Entity,
        changes: EntityChange[],
        deletions: EntityDeletion[],
        deleted: Set<string>,
        sources: Map<string, LinkSource>
    ): void
    {
        // pending rather than every change, so that a user who decided to leave the associations to the
        // other write has that decision honored the way it is for a scalar
        for (const name of pending(entity))
        {
            const relation = MergeMeta.linkRelation(entity.type, name)

            if (!relation)
            {
                continue
            }

            const value = entity.changes.get(name)

            const base = linkBase(entity, relation)
            const wanted = targetIds(value as any[], relation)
            const held = targetIds(base, relation)

            for (const link of base)
            {
                if (wanted.has(targetIdOf(link, relation)))
                {
                    continue
                }

                const id = linkIdOf(link, entity, relation)
                const known = this.entities.get(key(relation.linkType, id))

                if (known?.gone)
                {
                    // somebody else removed the association already, which is the outcome this deletion
                    // was for. Nothing to write, and sending it again would only fail the merge over a
                    // state the user asked for and has
                    continue
                }

                if (known?.unversioned)
                {
                    // the link row has no base to hold its deletion to, and the query that read it is
                    // where that is fixed
                    throw new Error(known.unversioned)
                }

                if (!deleted.has(key(relation.linkType, id)))
                {
                    deleted.add(key(relation.linkType, id))
                    deletions.push({type: relation.linkType, id, version: known?.version ?? null})
                    sources.set(key(relation.linkType, id), {type: entity.type, id: entity.id, field: name})
                }
            }

            for (const link of value as any[])
            {
                const targetId = targetIdOf(link, relation)

                if (held.has(targetId))
                {
                    continue
                }

                held.add(targetId)

                if (entity.linked.get(name)?.has(targetId))
                {
                    // somebody else made this association already, which is the outcome this insert was
                    // for. Asking for it again is the constraint on the pair refusing it again
                    continue
                }

                if (this.known(link)?.isNew)
                {
                    // a link row the application made itself, e.g. because the link type carries a field of
                    // its own. It is a row of this working set and goes out as one, foreign keys and all.
                    continue
                }

                const id = uuid()
                sources.set(key(relation.linkType, id), {
                    type: entity.type,
                    id: entity.id,
                    field: name,
                    target: targetId
                })

                changes.push({
                    type: relation.linkType,
                    id,
                    version: null,
                    new: true,
                    changes: [
                        linkField(relation.linkType, relation.sourceField, entity.id),
                        linkField(relation.linkType, relation.targetField, targetId)
                    ]
                })
            }
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
            if (MergeMeta.linkRelation(entity.type, name))
            {
                // no field of this row at all: it becomes inserts and deletions of the link type, which
                // diffLinks() makes
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
 * show it to -- is in here as undefined rather than left out: that the field moved is worth marking in the
 * form whether or not there is a value to put next to it.
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
 * The links the given entity was registered with, which a write to that field is diffed against. Empty for
 * an entity that was created here and therefore has no associations yet.
 */
function linkBase(entity: Entity, relation: MergeMeta.LinkRelation): any[]
{
    return (entity.base.get(relation.field) as any[]) ?? []
}


/**
 * The rows the given links associate with, by id. A set, because what a link array says is which rows are
 * associated -- naming one of them twice says nothing more than naming it once.
 */
function targetIds(links: any[], relation: MergeMeta.LinkRelation): Set<string>
{
    return new Set(links.map(link => targetIdOf(link, relation)))
}


/**
 * The id of the row one link associates with, which is what identifies the link among its siblings: two
 * links of the same array to the same row are one association.
 *
 * Read from the foreign key, or from the row on the other side where the link carries it. That second form
 * is the short one -- `[...bar.bazLinks, {baz}]` -- and it is also the one a view can render straight away,
 * the association being the row rather than its id.
 */
function targetIdOf(link: any, relation: MergeMeta.LinkRelation): string
{
    const id = link?.[relation.targetField] ??
        (relation.targetObject ? link?.[relation.targetObject]?.id : undefined)

    if (typeof id !== "string" || id.length === 0)
    {
        throw new Error(
            `A ${relation.linkType} of ${relation.sourceType}.${relation.field} says nothing about which ` +
            `${relation.targetType} it links to. Give it "${relation.targetField}"` +
            (relation.targetObject ? ` or "${relation.targetObject}".` : ".")
        )
    }

    return id
}


/**
 * The id of a link row the merge is to delete.
 *
 * @throws if the row has none. A link that was read without its id cannot be deleted, and the query that
 *         read it is where that is fixed
 */
function linkIdOf(link: any, entity: Entity, relation: MergeMeta.LinkRelation): string
{
    const id = link?.id

    if (typeof id !== "string" || id.length === 0)
    {
        throw new Error(
            `A ${relation.linkType} of ${entity.type} ${entity.id} was taken out of ` +
            `"${relation.field}" and has no id, so there is nothing to delete. Select "id" on ` +
            `"${relation.field}" in the query the rows came from.`
        )
    }

    return id
}


/**
 * One foreign key of a new link row, in the form the mutation takes it.
 */
function linkField(linkType: string, name: string, value: string): FieldChange
{
    return {field: name, value: {type: scalarTypeName(linkType, name), value}}
}


/**
 * Whether a field written to a draft is the value the row was registered with.
 *
 * A converted value is an object rather than a primitive -- a Timestamp is a Temporal.Instant -- and two of
 * them holding the same instant are not the same object. What knows they are equal is the value itself, so
 * an equals() is asked wherever there is one.
 */
function sameValue(a: unknown, b: unknown): boolean
{
    if (Object.is(a, b))
    {
        return true
    }

    if (a === null || b === null || typeof a !== "object" || typeof b !== "object")
    {
        return false
    }

    return typeof (a as any).equals === "function" && (a as any).equals(b)
}
