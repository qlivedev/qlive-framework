/*
    Generated types. Do *not* edit. Run "pnpm generate" to update from schema.graphql
*/
import { QueryConfig, Temporal } from "@quinscape/qlive-ts"

/** Database storage for spring security's remember-me feature */
export type AppLogin = {

    _type: "AppLogin",

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

    _type: "AppUser",

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

    _type: "AppUserDocument",

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

    _type: "AppVersion",

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

    _type: "Bar",

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

    _type: "BarDocument",

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

    _type: "BarLink",

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

    _type: "Baz",

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

    _type: "BazDocument",

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

    _type: "Foo",

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

    _type: "FooDocument",

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

    _type: "FooType",

    /** DB column 'name' */
    name: string
    /** DB column 'ordinal' */
    ordinal: number
}

/** Container for FooType queries */
export type FooTypeDocument = {

    _type: "FooTypeDocument",

    /** query config for this document */
    config: QueryConfig
    rowCount?: number
    /** List of FooType objects */
    rows: FooType[]
    /** Runtime payload type (always 'FooType') */
    type: string
}

/** Auto-generated from QueryLogic */
export type MutationType = {

    _type: "MutationType",

    mDummy?: boolean
}

/** Auto-generated from QueryLogic */
export type QueryType = {

    _type: "QueryType",

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

    _type: "Qux",

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

    _type: "QuxDocument",

    /** query config for this document */
    config: QueryConfig
    rowCount?: number
    /** List of Qux objects */
    rows: Qux[]
    /** Runtime payload type (always 'Qux') */
    type: string
}

export type DomainObject = AppLogin | AppUser | AppUserDocument | AppVersion | Bar | BarDocument | BarLink | Baz |
    BazDocument | Foo | FooDocument | FooType | FooTypeDocument | MutationType | QueryType | Qux |
    QuxDocument
