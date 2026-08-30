/*
    Generated types. Do *not* edit. Run "pnpm generate" to update from schema.graphql
*/
import {QueryDocumentAPI} from "@quinscape/qlive-ts"

/** Generated for de.quinscape.qlive.model.dev.AllScalars */
export type AllScalars = {

    _type: "AllScalars",

    bigDecimalValue?: bigint
    bool?: boolean
    byteValue?: number
    computedValueScalar?: ComputedValue
    conditionValue?: ConditionNode
    currencyValue?: number
    dateValue?: Temporal.PlainDate
    domainObjectValue?: DomainObject
    doubleValue?: number
    fieldExpressionValue?: string | FieldNode
    genericScalarValue?: GenericScalar
    intValue?: number
    jsonbValue?: any
    longValue?: number
    queryConfigValue?: QueryConfig
    stringValue?: string
    timestampValue?: Temporal.Instant
}

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
    /** List of AppUser objects */
    rows: AppUser[]
    /** Runtime payload type (always 'AppUser') */
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
    /** List of FooType objects */
    rows: FooType[]
    /** Runtime payload type (always 'FooType') */
    type: string
}

/** Auto-generated from QueryDocumentLogic */
export type MutationType = {

    _type: "MutationType",

    mDummy?: boolean
}

/** Auto-generated from QueryDocumentLogic */
export type QueryType = {

    _type: "QueryType",

    queryAllScalars?: AllScalars
    /** Queries AppUser objects based on the given query config */
    queryAppUserDocument?: AppUserDocument
    /** Queries Foo objects based on the given query config */
    queryFooDocument?: FooDocument
    /** Queries FooType objects based on the given query config */
    queryFooTypeDocument?: FooTypeDocument
}

export type DomainObject =
    AllScalars
    | AppLogin
    | AppUser
    | AppUserDocument
    | Foo
    | FooDocument
    | FooType
    | FooTypeDocument
    |
    MutationType
    | QueryType
