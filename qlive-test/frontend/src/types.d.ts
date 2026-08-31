/*
    Generated types. Do *not* edit. Run "pnpm generate" to update from schema.graphql
*/
import { QueryDocumentAPI } from "@quinscape/qlive-ts"
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
}

export type DomainObject = AppLogin | AppUser | AppUserDocument | Bar | BarDocument | BarLink | Baz | BazDocument |
    Foo | FooDocument | FooType | FooTypeDocument | MutationType | QueryType
