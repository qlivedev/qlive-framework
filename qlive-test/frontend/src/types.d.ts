/*
    Generated types. Do *not* edit. Run "pnpm generate" to update from schema.graphql
*/
import { GenericScalar, QueryConfig, Temporal } from "@quinscape/qlive-ts"

/** How a merge ended.

Two outcomes and no third one. Everything that is neither -- a type nobody exposes, a field name that
matches no column, a constraint the database refuses -- is a programming error rather than a state of the
data, and comes back as a GraphQL error instead of as a status a caller has to branch on. */
export type MergeStatus =
    /** At least one row could not be written as asked, and nothing was written at all. The result carries one
conflict per row that stood in the way. */
    "CONFLICT" |
    /** Everything in the working set landed. Nothing else is written, and the transaction committed. */
    "DONE"

/** The ordered field-name list one field mask was written against, under the hash of that list. A version record names its layout, so a mask written before a deployment that reordered a type's fields is permuted back into today's positions instead of being read as a different set of fields. Written idempotently -- the id being the hash makes it an upsert -- and swept by the same task that prunes version records, minus the layout currently in use and minus anything stored too recently to have been used yet. */
export type AppFieldLayout = {
    /** When this layout was first stored in this database. What keeps the sweep from taking a layout a node of a rolling deployment has stored but not yet written a record against, and the only account of when a layout appeared once the records naming it have been pruned. */
    created: Temporal.Instant
    /** GraphQL name of the type the layout belongs to. In the hashed input as well, so that two types whose field lists happen to be identical do not share a row and "what did this type look like then" stays answerable. */
    entityType: string
    /** The field names in the order that assigns the mask bit indices, joined by a character no GraphQL name can contain. Kept rather than only hashed: a hash detects that the bits moved, the list is what moves them back. */
    fields: string
    /** SHA-256 of the type name and the ordered field names, which is what makes writing a layout an upsert rather than a decision */
    id: string
}

/** Database storage for spring security's remember-me feature */
export type AppLogin = {
    /** Last access of the login */
    lastUsed: Temporal.Instant
    /** Token series */
    series: string
    /** Token */
    token: string
    /** User name of the login */
    username: string
}

/** Application users. Used to authenticate users by spring security. Can have additional fields/relations (See UserInfoService) */
export type AppUser = {
    /** Creation date of the user entry */
    created: Temporal.Instant
    /** true if the user account was disabled */
    disabled?: boolean
    /** Many-to-many objects from foo.owner_id */
    foos: Foo[]
    /** user database id */
    id: string
    /** last login of the user */
    lastLogin?: Temporal.Instant
    /** User name / login */
    login: string
    /** encrypted password */
    password: string
    /** Spring security roles of the user within the application */
    roles: string
}

/** Container for AppUser queries */
export type AppUserDocument = {
    /** query config for this document */
    config: QueryConfig
    rowCount?: number
    /** List of AppUser objects */
    rows: AppUser[]
    /** Runtime payload type (always 'AppUser') */
    type: string
}

/** One recorded change to a row of a versioned type: who made it, when, and which fields it touched. A row's version column names the record describing the state it is in now, and prev chains that record to the one before it, so the fields changed between any two versions are the union of the masks in between. Written only by the merge, pruned after the version record lifetime -- which is why nothing has a foreign key onto it: a row outlives the records describing how it got here. */
export type AppVersion = {
    /** When the change was made. What the cleanup of expired version records goes by. */
    created: Temporal.Instant
    /** Id of the row that changed */
    entityId: string
    /** GraphQL name of the type whose row changed */
    entityType: string
    /** Hash of the field-name list the mask was written against. A record whose layout does not match the schema in front of us has its mask read as unknown, so a deployment that reorders fields makes a mask conservative rather than wrong. */
    fieldLayout: string
    /** Bit per field of the type, set for the fields this change touched. The bit index is the position of the field in the type's alphabetically sorted field list, and fieldLayout is what makes reading that back safe. */
    fieldMask: bigint
    /** Version id, and what the version column of the changed row holds */
    id: string
    /** The user who made the change. What lets a subscriber filter out its own writes. */
    ownerId: string
    /** The version this change was made against, or null for the first recorded change of the row. Best-effort: the record it names may have been pruned, which reads as "assume every field changed" rather than as an error. */
    prev?: string
}

/** Generated from public.bar */
export type Bar = {
    /** Many-to-many objects from bar_link.bar_id */
    bazLinks: BarLink[]
    /** DB column 'created' */
    created: Temporal.Instant
    /** DB column 'description' */
    description?: string
    /** DB column 'id' */
    id: string
    /** DB column 'name' */
    name: string
    /** DB column 'num' */
    num: number
    /** DB column 'version' */
    version?: string
}

/** Container for Bar queries */
export type BarDocument = {
    /** query config for this document */
    config: QueryConfig
    rowCount?: number
    /** List of Bar objects */
    rows: Bar[]
    /** Runtime payload type (always 'Bar') */
    type: string
}

/** Generated from public.bar_link */
export type BarLink = {
    /** Target of 'bar_id' */
    bar: Bar
    /** DB foreign key column 'bar_id' */
    barId: string
    /** Target of 'baz_id' */
    baz: Baz
    /** DB foreign key column 'baz_id' */
    bazId: string
    /** DB column 'id' */
    id: string
    /** DB column 'version' */
    version?: string
}

/** Generated from public.baz */
export type Baz = {
    /** Many-to-many objects from bar_link.baz_id */
    bazLinks: BarLink[]
    /** DB column 'created' */
    created: Temporal.Instant
    /** DB column 'description' */
    description?: string
    /** DB column 'id' */
    id: string
    /** DB column 'name' */
    name: string
    /** DB column 'num' */
    num: number
    /** DB column 'version' */
    version?: string
}

/** Container for Baz queries */
export type BazDocument = {
    /** query config for this document */
    config: QueryConfig
    rowCount?: number
    /** List of Baz objects */
    rows: Baz[]
    /** Runtime payload type (always 'Baz') */
    type: string
}

/** Example domain type */
export type Foo = {
    /** Foo create timestamp */
    created: Temporal.Instant
    /** Foo description' */
    description?: string
    /** Boolean example */
    flag: boolean
    /** Target of 'type' */
    fooType: FooType
    id: string
    /** Foo name */
    name: string
    /** Number example */
    num: number
    /** Target of 'owner_id' */
    owner: AppUser
    /** DB foreign key column 'owner_id' */
    ownerId: string
    /** DB foreign key column 'type' */
    type: string
    /** Id of the app_version record describing the state this row is in now. Selected by any query whose rows are to be edited -- the merge checks its write against it, and a working set cannot register a row without one. */
    version?: string
}

/** Container for Foo queries */
export type FooDocument = {
    /** query config for this document */
    config: QueryConfig
    rowCount?: number
    /** List of Foo objects */
    rows: Foo[]
    /** Runtime payload type (always 'Foo') */
    type: string
}

/** Generated from public.foo_type */
export type FooType = {
    /** DB column 'name' */
    name: string
    /** DB column 'ordinal' */
    ordinal: number
}

/** Container for FooType queries */
export type FooTypeDocument = {
    /** query config for this document */
    config: QueryConfig
    rowCount?: number
    /** List of FooType objects */
    rows: FooType[]
    /** Runtime payload type (always 'FooType') */
    type: string
}

/** One row the merge could not write, and why.

A conflict is data. The framework ships no dialog and nothing here blocks: the merge rolled back, the user
still has everything they typed, and the form they were editing is where they decide what to do about it. */
export type MergeConflict = {
    /** true if the row is not there at all -- removed by somebody else, or never created. There is nothing to
merge into and nothing to choose between, so no fields come with it. */
    deleted: boolean
    /** The fields that clashed. Empty for a deletion, which touches no fields, and empty where the row is
gone. */
    fields: MergeConflictField[]
    /** Id of the row. */
    id: string
    /** The version standing in the database now, and the base a second attempt has to be made against. Null
where the row is gone, and null for a type that carries no version field.

Not called "version", and it cannot be: a type with a field of that name is a versioned type, on this
end and on the client, and that rule is what makes the two ends unable to disagree about who takes
part. A conflict is not a row and does not take part. The name it has instead is the one
MergeConflictField already uses for the value that is in the database. */
    storedVersion?: string
    /** GraphQL name of the type whose row this is. */
    type: string
}

/** One field of a row that could not be written as asked.

The vocabulary is deliberately not "ours" and "theirs". Whoever wrote first is gone; the only person still
here is the one whose save just bounced, and what they are choosing between is the value they typed and the
value that is in the database. So: mine and stored.

Not every field in here is a decision. A field the other write touched and this one did not is attached
as informational, so that a form can show what moved under the user rather than only what clashed. */
export type MergeConflictField = {
    /** Name of the field, as the GraphQL type spells it. */
    field: string
    /** true if the field is here to be seen rather than decided about: the other write changed it, this one
did not, and the merge takes their value silently. Catching up on a field nobody here has an opinion
about is not a decision anybody needs to make.

An informational field carries no mine, there being no value of ours to carry. */
    informational: boolean
    /** The value the user meant to write, echoed back. Null where the conflict carries no values, i.e. where
either the type or the caller did not ask to resolve conflicts. */
    mine?: GenericScalar
    /** The value that is in the database. Null where the conflict carries no values, and null as a value in
its own right where the stored value is null -- the GenericScalar is there either way when values are
carried at all. */
    stored?: GenericScalar
}

/** What came of one merge.

All or nothing: either every change and every deletion landed, or none of them did and the conflicts say
which rows stood in the way. There is no partial success to reconcile, which is what lets a working set
keep holding exactly what the user has not saved yet. */
export type MergeResult = {
    /** One entry per row that could not be written. Empty when the merge is done. */
    conflicts: MergeConflict[]
    /** Whether the merge landed. */
    status: MergeStatus
}

/** Auto-generated from QueryLogic, MergeLogic */
export type MutationType = {
    mergeWorkingSet: MergeResult
}

/** Auto-generated from QueryLogic, MergeLogic */
export type QueryType = {
    /** Queries AppUser objects based on the given query config */
    queryAppUserDocument: AppUserDocument
    /** Queries Bar objects based on the given query config */
    queryBarDocument: BarDocument
    /** Queries Baz objects based on the given query config */
    queryBazDocument: BazDocument
    /** Queries Foo objects based on the given query config */
    queryFooDocument: FooDocument
    /** Queries FooType objects based on the given query config */
    queryFooTypeDocument: FooTypeDocument
    /** Queries Qux objects based on the given query config */
    queryQuxDocument: QuxDocument
}

/** Every scalar type the framework supports, one column each, and the example of a schema type a hand-written class stands in for -- see com.dataciders.qlivetest.model.types.Qux, which is where the documentation of the fields no column backs would otherwise live. */
export type Qux = {
    /** DB column 'big_decimal_value' */
    bigDecimalValue?: bigint
    /** DB column 'bool' */
    bool?: boolean
    /** DB column 'byte_value' */
    byteValue?: number
    /** DB column 'currency_value' */
    currencyValue?: number
    /** DB column 'date_value' */
    dateValue?: Temporal.PlainDate
    /** DB column 'double_value' */
    doubleValue?: number
    /** DB column 'id' */
    id: string
    /** DB column 'int_value' */
    intValue?: number
    /** JSONB example. An object, so that what comes back is read as one and not as the string it travels as. */
    jsonbValue?: any
    /** DB column 'long_value' */
    longValue?: number
    /** Qux name */
    name: string
    /** DB column 'string_value' */
    stringValue?: string
    /** Name and string value of the row, computed on read. No column backs it, so nothing fetches one on its account: a query wanting this should select the fields it is computed from as well. */
    summary?: string
    /** DB column 'timestamp_value' */
    timestampValue?: Temporal.Instant
}

/** Container for Qux queries */
export type QuxDocument = {
    /** query config for this document */
    config: QueryConfig
    rowCount?: number
    /** List of Qux objects */
    rows: Qux[]
    /** Runtime payload type (always 'Qux') */
    type: string
}

export type DomainObject = AppFieldLayout | AppLogin | AppUser | AppUserDocument | AppVersion | Bar | BarDocument | BarLink |
    Baz | BazDocument | Foo | FooDocument | FooType | FooTypeDocument | MergeConflict | MergeConflictField |
    MergeResult | MutationType | QueryType | Qux | QuxDocument
