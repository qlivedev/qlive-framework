import {GenericScalar} from "../GraphQL";

/**
 * The model of QLive's write mutation, as the client sends and receives it.
 *
 * These are the same types the server declares in com.dataciders.qlive.model.merge, and an application's
 * generated types have them too -- they are part of its schema like everything else. They are declared here
 * as well because the working set is written against them and the framework cannot import an application's
 * generated file.
 */

/**
 * How a merge ended: everything landed, or nothing did.
 */
export type MergeStatus = "DONE" | "CONFLICT"

/**
 * One field of one row set to one value. The value travels as a generic scalar, which is what lets one
 * mutation write every type in the domain.
 */
export type FieldChange = {
    field: string
    value: GenericScalar | null
}

/**
 * Everything one row changed by: the row, the base it was read at, and the fields that changed. What the
 * user did not touch is not in here, which is what makes a concurrent change to another field mergeable.
 */
export type EntityChange = {
    type: string
    id: string
    version: string | null

    /**
     * true if the row does not exist yet. Said rather than guessed: an unversioned type has no version to
     * read the answer off, and a row somebody else deleted must not be recreated by an update that found
     * nothing.
     */
    new: boolean
    changes: FieldChange[]
}

/**
 * One row to remove, under the same optimistic lock a change is under.
 */
export type EntityDeletion = {
    type: string
    id: string
    version: string | null
}

/**
 * What the caller of a merge says about itself, as opposed to what a type declares once in the
 * application's MergeMetadataProvider.
 */
export type MergeConfig = {

    /**
     * true if the caller can put a conflict in front of a user and take a decision back. Only then does a
     * conflict come back carrying both values per field.
     */
    resolveConflicts: boolean
}

/**
 * One field of a row that could not be written as asked.
 *
 * "mine" is the value the user typed and "stored" the one that is in the database -- not "ours" and
 * "theirs", because whoever wrote first is gone and the only person still here is the one whose save just
 * bounced.
 */
export type MergeConflictField = {
    field: string
    mine: GenericScalar | null
    stored: GenericScalar | null

    /**
     * true if the field is here to be seen rather than decided about: the other write changed it, this one
     * did not, and the merge takes their value. It carries no "mine", there being no value of ours.
     */
    informational: boolean
}

/**
 * One row the merge could not write, and why.
 */
export type MergeConflict = {
    type: string
    id: string

    /**
     * The version standing in the database now, and the base a second attempt has to be made against. Null
     * where the row is gone and for a type carrying no version field.
     */
    storedVersion: string | null

    /**
     * true if the row is not there at all. Nothing to merge into and nothing to choose between, so no
     * fields come with it.
     */
    deleted: boolean
    fields: MergeConflictField[]
}

/**
 * What came of one merge. All or nothing: either everything landed or the conflicts say which rows stood in
 * the way and nothing was written.
 */
export type MergeResult = {
    status: MergeStatus
    conflicts: MergeConflict[]
}
