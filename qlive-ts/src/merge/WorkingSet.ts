import {v4 as uuid} from "uuid";
import {GraphQLQuery} from "../GraphQLQuery";
import {GraphQLField} from "../GraphQLSchema";
import {QueryConfigDelta} from "../QueryDocument";
import {findType, LIST, unwrapAll, unwrapNonNull} from "../type-utils";
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

    merge: () => Promise<MergeResult>
    undo: () => void
    clear: () => void
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
    private readonly resolveConflicts: boolean;

    /** documents the rows came from, kept so that a merge that landed can leave them holding fresh rows */
    private documents: RegisteredDocument[];

    /** every entity, by type and id */
    private entities: Map<string, Entity>;

    /** which entity a registered row belongs to, which is how edit() recognises a row it was handed */
    private rows: WeakMap<object, string>;

    private conflicts: MergeConflict[];

    private subscribers: (() => void)[];

    private snapshot: WorkingSetSnapshot | null;


    constructor(options: WorkingSetOptions = {})
    {
        this.resolveConflicts = options.resolveConflicts !== false
        this.documents = []
        this.entities = new Map()
        this.rows = new WeakMap()
        this.conflicts = []
        this.subscribers = []
        this.snapshot = null
    }


    /**
     * Registers every row of the given query document, and the rows below them.
     *
     * What it takes from a row is the version it was read at -- the base every write of it is held to --
     * and a copy of its scalar values, which is what a change is a change against. Anything with an id is
     * an entity, whatever type it is and however deep it sits, so registering the document a view renders
     * registers everything that view can edit.
     *
     * @param document      query document, or the snapshot a view holds of one
     *
     * @throws if a row of a versioned type carries no version, which is a base the merge cannot make up
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
            target: {id},
            draft: null
        }

        this.entities.set(key(type, id), entity)
        this.rows.set(entity.target, key(type, id))

        for (const [name, value] of Object.entries(values))
        {
            if (name !== "id")
            {
                this.change(entity, name, value)
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
        const entity = this.entityOf(row)

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

        for (const [name, value] of entity.changes)
        {
            out[name] = value
        }

        return out as unknown as T
    }


    /**
     * true while the working set holds anything unsaved.
     */
    get dirty(): boolean
    {
        for (const entity of this.entities.values())
        {
            if (entity.isNew || entity.deleted || entity.changes.size > 0)
            {
                return true
            }
        }

        return false
    }


    /**
     * Takes every change back, leaving the rows as they were registered. Conflicts go with them: they
     * describe a write that no longer exists.
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
                this.diffLinks(entity, changes, deletions, deleted)
            }
        }

        if (changes.length === 0 && deletions.length === 0)
        {
            // Nothing to write, so nothing to refresh either: the documents are holding what a merge would
            // have gone and fetched again. A form that saves an untouched row costs a round trip otherwise.
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
                const entity = this.entities.get(key(conflict.type, conflict.id))

                if (entity && conflict.storedVersion)
                {
                    // The base moves to what is in the database, which is what makes a second save possible
                    // at all. Every conflict stands resolved as the user's own value until they say
                    // otherwise -- the person present typed it on purpose, and the one who did not is not
                    // here to argue.
                    entity.version = conflict.storedVersion
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
                merge: this.merge,
                undo: this.undo,
                clear: this.clear
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

        for (const row of document.rows)
        {
            this.walkRow(row, document.type, source)
        }
    }


    /**
     * Registers one row and everything below it.
     *
     * The type says which fields are rows of their own and which are values, so no marker has to travel
     * with the data. A row without an id is no entity -- there is nothing to name it by and nothing to
     * hang a change on -- but the rows below it still are, since a query is free to select an object
     * without selecting its id.
     */
    private walkRow(row: any, type: string, source: string): void
    {
        if (!row || typeof row !== "object")
        {
            return
        }

        const base = new Map<string, unknown>()

        for (const field of objectFields(type))
        {
            const value = row[field.name]
            if (value === undefined)
            {
                // a field the query did not select
                continue
            }

            const named = unwrapAll(field.type)

            if (named.kind === "OBJECT")
            {
                const rows = Array.isArray(value) ? value : [value]
                rows.forEach(nested => this.walkRow(nested, named.name!, source))

                if (Array.isArray(value) && MergeMeta.linkRelation(type, field.name))
                {
                    // the associations as they stand, which is what a write to the field is diffed against.
                    // A copy of the array and not the array: the one the row holds is the view's to render
                    // and is free to be replaced.
                    base.set(field.name, [...value])
                }
            }
            else
            {
                base.set(field.name, value)
            }
        }

        const id = row.id
        if (typeof id !== "string" || id.length === 0)
        {
            return
        }

        const version = version_(type, id, base, source)

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

                return typeof name === "string" && entity.changes.has(name)
                    ? entity.changes.get(name)
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
     * Records one field of one entity as changed, or takes the change back where the value is what the row
     * was registered with -- a field the user typed over and then typed back is not a change, and a row
     * whose every change came back is not dirty.
     */
    private change(entity: Entity, name: string, value: unknown): void
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
            this.changeLinks(entity, relation, value)
            return
        }

        if (unwrapAll(fieldOf(entity.type, name).type).kind === "OBJECT")
        {
            throw new Error(`Cannot change ${entity.type}.${name}: it is not a scalar field.`)
        }

        const next = value === undefined ? null : value

        if (entity.base.has(name) && sameValue(entity.base.get(name), next))
        {
            entity.changes.delete(name)
        }
        else
        {
            entity.changes.set(name, next)
        }

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
    private changeLinks(entity: Entity, relation: MergeMeta.LinkRelation, value: unknown): void
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

        this.notify()
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
        entity: Entity, changes: EntityChange[], deletions: EntityDeletion[], deleted: Set<string>
    ): void
    {
        for (const [name, value] of entity.changes)
        {
            const relation = MergeMeta.linkRelation(entity.type, name)

            if (!relation)
            {
                continue
            }

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

                if (!deleted.has(key(relation.linkType, id)))
                {
                    deleted.add(key(relation.linkType, id))
                    deletions.push({
                        type: relation.linkType,
                        id,
                        version: this.entities.get(key(relation.linkType, id))?.version ?? null
                    })
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

                if (this.known(link)?.isNew)
                {
                    // a link row the application made itself, e.g. because the link type carries a field of
                    // its own. It is a row of this working set and goes out as one, foreign keys and all.
                    continue
                }

                changes.push({
                    type: relation.linkType,
                    id: uuid(),
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

        for (const [name, value] of entity.changes)
        {
            if (MergeMeta.linkRelation(entity.type, name))
            {
                // no field of this row at all: it becomes inserts and deletions of the link type, which
                // diffLinks() makes
                continue
            }

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
 * The version a row of the given type was read at, or null where the type carries none.
 *
 * @throws if a versioned type's row has no version. That is a base the merge cannot make up, and finding
 *         out here is much better than finding out at merge time, where the answer would be a lost update
 */
function version_(type: string, id: string, base: Map<string, unknown>, source: string): string | null
{
    if (!MergeMeta.isVersioned(type))
    {
        return null
    }

    const version = base.get(MergeMeta.VERSION)

    if (typeof version === "string" && version.length > 0)
    {
        return version
    }

    throw new Error(
        base.has(MergeMeta.VERSION)
            ? `${type} ${id} has no version. Every row of a versioned type gets one when the merge writes ` +
            `it, so a row without one predates the column and has to be given one before it can be edited.`
            : `${type} ${id} was registered without its version. '${type}' is versioned, so the merge writes ` +
            `its rows against the version they were read at -- select "${MergeMeta.VERSION}" in ${source}.`
    )
}


/**
 * The fields of the given object type.
 */
function objectFields(type: string): GraphQLField[]
{
    const found = findType(type)

    if (found.kind !== "OBJECT" || !found.fields)
    {
        throw new Error(`"${type}" is a ${found.kind}, and a working set holds rows of object types.`)
    }

    return found.fields
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
