/**
 * What a form reads a working set through: the state of one row's fields, and what a decision about one of
 * them does.
 *
 * This is layer 2 of the three the design names -- above the store, below the hooks -- and nothing in it is
 * React. It is a plain object with functions on it, built by the working set and testable without rendering
 * anything, which is what lets a form library adopt the merge instead of reimplementing it. useMerge() is
 * the three lines over it.
 */

/**
 * Which values a read returns: the user's own edits, what is in the database, or the two folded together.
 *
 * "merged" is the default and the one a form wants: the user's edits, with the fields somebody else moved
 * and this user has no opinion about taken silently. The other two are what a "show me what I typed" and a
 * "show me what is saved" toggle switch to, and a form that knows nothing about merging renders all three
 * correctly, because the draft is what decides.
 */
export type MergeView = "mine" | "stored" | "merged"

/**
 * Which of two values the user chose for a field both writes changed.
 */
export type Resolution = "mine" | "stored"

/**
 * What has happened to one field.
 *
 * - `unchanged` -- nobody touched it since the row was read
 * - `changed` -- the user changed it and nobody else did
 * - `conflict` -- both changed it, and the user's value is the one standing
 * - `resolved` -- both changed it and the user looked at it and chose
 * - `moved` -- somebody else changed it and the user did not, so their value was taken
 */
export type MergeFieldStatus = "unchanged" | "changed" | "conflict" | "resolved" | "moved"

/**
 * The class one status carries, which is what an application puts on its input. They are styled in
 * qlive.css, in the qlive layer, so anything the application writes outside that layer wins.
 */
const CLASSES: Record<MergeFieldStatus, string> = {
    unchanged: "",
    changed: "qlive-changed",
    conflict: "qlive-conflict",
    resolved: "qlive-conflict-resolved",
    moved: "qlive-moved"
}

/**
 * One entity as the accessor reads it. The working set's own is this and more; naming only what is read
 * here is what keeps this module from depending on the store it is handed a piece of.
 */
export type MergeEntity = {
    type: string
    id: string

    /** what the database holds now, for the fields somebody else moved */
    stored: Map<string, unknown>

    /** what the user set */
    changes: Map<string, unknown>

    /** what the user decided about a field both writes changed */
    resolutions: Map<string, Resolution>

    /** the row as it was read, which is what a value falls back to */
    target: Record<string, any>

    /** true where the row is not in the database any more */
    gone: boolean
}

/**
 * What the accessor needs the working set to do. An object the store hands over rather than the store
 * itself: a decision has to reach the subscribers, and this is the whole of what that takes.
 */
export type MergeHost = {
    view(): MergeView
    resolve(entity: MergeEntity, field: string, choice: Resolution): void
    resolveWith(entity: MergeEntity, field: string, value: unknown): void
    accessor(row: object): MergeAccessor
}

/**
 * The state of one field of one row, as of the moment it was asked for.
 *
 * A plain object off an ordinary function call, not a hook -- so a generic renderer asks for one per field
 * in a loop, in a callback, or in a child component it handed the accessor to.
 */
export type MergeField = {
    name: string

    status: MergeFieldStatus

    /**
     * The class the status carries, empty for a field nothing happened to. Goes on the input:
     * `className={ field.className }`.
     */
    className: string

    /**
     * What the current view shows, which is what the draft returns for this field.
     */
    value: unknown

    /**
     * The value the user has, whatever the view is: what they typed, or what the row was read with where
     * they typed nothing.
     */
    mine: unknown

    /**
     * What is in the database, as far as this working set has been told: the other write's value for a
     * field it moved, and the value the row was read with otherwise.
     */
    stored: unknown

    /**
     * true while both writes changed this field and nobody has said which of the two wins.
     */
    conflict: boolean

    /**
     * What the user chose, or null where they have not been asked or have not answered.
     */
    resolution: Resolution | null

    /**
     * Takes the user's decision about a field both writes changed.
     *
     * Only ever a correction: a conflict comes back already standing as `mine`, because the person present
     * typed that value on purpose and the person who did not is not here to argue. Choosing `stored` leaves
     * their value in place and does not write ours -- and does not throw it away either, so choosing `mine`
     * afterwards brings it back.
     *
     * @throws if nobody else wrote this field, there being nothing to choose between
     */
    resolve(choice: Resolution): void

    /**
     * Takes a third value, neither the user's nor the stored one, and records that the user decided it.
     *
     * Separate from resolve() rather than an overload of it: the choices are strings, and a field whose
     * value is a string could not tell `resolve("stored")` from a user meaning to store the word.
     */
    resolveWith(value: unknown): void
}

/**
 * The merge state of one row, and the way to a decision about any of its fields.
 *
 * One of these per entity rather than per field: a hook cannot be called from a loop over a field list, so
 * an accessor is what lets a form render itself from a field list that comes from the schema, a config, or
 * anywhere else.
 */
export type MergeAccessor = {
    type: string
    id: string

    /**
     * true where the last merge found the row gone: somebody else deleted it. There is nothing to merge
     * into and nothing to choose between, so no field of it carries a conflict.
     */
    gone: boolean

    field(name: string): MergeField

    /** fields the user changed, alphabetically */
    changedFields(): string[]

    /** fields both writes changed and nobody has decided about, alphabetically */
    conflictedFields(): string[]

    /** fields both writes changed and the user has decided about, alphabetically */
    resolvedFields(): string[]

    /** fields somebody else changed and the user did not, alphabetically */
    movedFields(): string[]

    /**
     * The accessor for another row of the same working set -- what a form editing a Bar and its BarLink
     * rows in one place needs, without a second hook per row.
     *
     * @param row       row of a registered document, or a draft of one
     */
    of(row: object): MergeAccessor
}


/**
 * What has happened to one field of one entity.
 *
 * Derived from the state rather than remembered from a merge response, which is what lets a push message
 * saying "this field moved" produce the same answer as a conflict does.
 */
export function statusOf(entity: MergeEntity, name: string): MergeFieldStatus
{
    if (entity.stored.has(name))
    {
        if (entity.resolutions.has(name))
        {
            return "resolved"
        }

        return entity.changes.has(name) ? "conflict" : "moved"
    }

    return entity.changes.has(name) ? "changed" : "unchanged"
}


/**
 * The value one field has in one view.
 *
 * The one place the view flag means anything, and the reason a form needs no second version of itself: a
 * draft read comes through here, so switching the flag re-renders the whole form against other values
 * without a single input knowing about it.
 */
export function valueOf(entity: MergeEntity, name: string, view: MergeView): unknown
{
    const takeStored = view === "stored" ||
        (view === "merged" && entity.resolutions.get(name) === "stored")

    if (!takeStored && entity.changes.has(name))
    {
        return entity.changes.get(name)
    }

    if (view !== "mine" && entity.stored.get(name) !== undefined)
    {
        // undefined rather than null is a field known to have moved and not known to what -- a type that
        // did not opt in to resolution carries no values -- and what a form shows for one of those is what
        // the row was read with
        return entity.stored.get(name)
    }

    return entity.target[name]
}


/**
 * The accessor for one entity. Made by the working set and thrown away whenever it changes, so what a view
 * holds is a snapshot like every other one.
 */
export function createAccessor(host: MergeHost, entity: MergeEntity): MergeAccessor
{
    const withStatus = (wanted: MergeFieldStatus) =>
        [...new Set([...entity.changes.keys(), ...entity.stored.keys()])]
            .filter(name => statusOf(entity, name) === wanted)
            .sort()

    return {
        type: entity.type,
        id: entity.id,
        gone: entity.gone,

        field: (name: string): MergeField =>
        {
            const status = statusOf(entity, name)

            return {
                name,
                status,
                className: CLASSES[status],
                value: valueOf(entity, name, host.view()),
                mine: valueOf(entity, name, "mine"),
                stored: valueOf(entity, name, "stored"),
                conflict: status === "conflict",
                resolution: entity.resolutions.get(name) ?? null,
                resolve: choice => host.resolve(entity, name, choice),
                resolveWith: value => host.resolveWith(entity, name, value)
            }
        },

        changedFields: () => [...entity.changes.keys()].sort(),
        conflictedFields: () => withStatus("conflict"),
        resolvedFields: () => withStatus("resolved"),
        movedFields: () => withStatus("moved"),

        of: (row: object) => host.accessor(row)
    }
}
