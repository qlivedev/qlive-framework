import type {ComponentType} from "react";

import type {QueryConfigDelta} from "./QueryDocument";
import type {MergeTypeMeta} from "./merge/meta";

import {GraphQLSchema, GraphQLType} from "./GraphQLSchema";
import {addData, initData} from "./data";
import {initConverters} from "./converter";
import DefaultErrorView, {ErrorViewProps} from "./component/ErrorView";

/**
 * Meta-information about types that are generic types on the Java side.
 *
 * For example, io.github.qlivedev.model.QueryDocument<T> is the generic Java class for query documents. In the GraphQL
 * world, the same type is called FooDocument with Foo replacing the generic T.
 */
export type GenericTypeInfo = {
    /**
     * GraphQL Type name
     */
    type: string,
    /**
     * TypeParameters as GraphQL type names
     */
    typeParameters: string[]
    /**
     * Full-qualified Java class name of the original generic class. Mostly useful as constant in the TS world
     */
    genericType: "io.github.qlivedev.model.QueryDocument" | string
}

/**
 * Describe a relation within the domain
 */
export type RelationInfo = {

    /**
     * Right side type of the relation
     */
    targetType: string,
    /**
     * Generated GraphQL field of the left / source side of the relation
     */
    leftSideObjectName?: string,
    /**
     * Generated GraphQL field of the right / target side of the relation
     */
    rightSideObjectName?: string,

    /**
     * SourceField enum. Controlled the GraphQL fields on the left side
     */
    sourceField: SourceField,
    /**
     * Target field enum. Controlled the GraphQL fields on the right side
     */
    targetField: TargetField,

    /**
     * Left side type of the relation
     */
    sourceType: string,
    /**
     * Meta tags
     */
    metaTags: string[],

    /**
     * Name of the relation
     */
    id?: string,

    /**
     * Fields on the left side table forming the foreign key
     */
    sourceFields: string[],

    /**
     * Fields on the right side table the foreign key is pointing to
     */
    targetFields: [string]
}

/**
 * One end of a declared many-to-many: its type, the field of the link type pointing at it, and the field it gets
 * listing the rows on the other end.
 */
export type ManyToManyEnd = {

    /**
     * Domain type of this end, e.g. "Bar"
     */
    type: string

    /**
     * Field of the link type pointing at this end, e.g. "barId"
     */
    linkField: string

    /**
     * Field on this end's type listing the rows on the other end, e.g. "bazs". Absent where the declaration
     * gave this end none.
     */
    field?: string
}

/**
 * A many-to-many declared with configureManyToMany() or withManyToMany(): a link type whose rows each associate
 * a row of one end with a row of the other.
 */
export type ManyToManyInfo = {

    /**
     * Domain type of the link table, e.g. "BarLink"
     */
    linkType: string

    /**
     * End the declaration's first link column points at
     */
    left: ManyToManyEnd

    /**
     * End the declaration's second link column points at
     */
    right: ManyToManyEnd

    /**
     * Whether the fields of both ends can be written, i.e. whether a link row can be inserted from its two link
     * columns alone
     */
    writable: boolean
}
/**
 * Source field configuration for a relation. Defines what fields to add for the relation on the side where the foreign
 * key is.
 */
export type SourceField =
/**
 * Ignore field for source type.
 */
    "NONE" |

    /**
     * Define a scalar GraphQL field for the key itself (e.g. fooId : string)
     */
    "SCALAR" |

    /**
     * Define an embedded object for the target
     */
    "OBJECT" |

    /**
     *  Define a field for the key iteself *and* define an embedded object.
     *
     *  This is useful in situations where you want the embedded object in some cases, but in others you
     *  want to save one level of querying because all you need is the target id.
     */
    "OBJECT_AND_SCALAR"

/**
 * Target field configuration for a relation. Defines what fields should be added for the relation to the side the
 * foreign key points to.
 */
export type TargetField =
    /**
     * Do nothing on target side.
     */
    "NONE" |
    /**
     * Assume the foreign key to represent a one-to-one relationship and embed a single object as back reference.
     */
    "ONE" |
    /** Assume the foreign key to represent a many-to-one relationship and embed a list of back references.*/
    "MANY"

/**
 * Injection as it comes in over the wire.
 */
export type InjectionSource = {
    data: any,
    type: string,
    meta: any,
}

/**
 * Injection with converted value.
 */
export type Injection = {
    value: any,
    type: string,
    meta: any
}

/**
 * Cross-Site Request-Forging protection token handling.
 */
export type CSRFToken = {
    /**
     * Name of the hidden input field carrying the token inside an HTML form POST
     */
    param: string
    /**
     * HTTP header that contains the token value for a fetch POST or similar.
     */
    header: string

    /**
     * CSRF token value
     */
    value: string
}

/**
 * Who the page is being served to, as AppAuthentication describes them on the Java side.
 *
 * Anonymous is an answer and not an absence: a page served to nobody in particular carries the anonymous
 * login, its role and its fixed id, so a reader never has to handle "not logged in" as a missing value.
 */
export type Authentication = {
    /**
     * Login name of the current user
     */
    login: string
    /**
     * Roles of the current user.
     */
    roles: string[]
    /**
     * Id of the current user (Usually pointing to the app_user table)
     */
    id: string
}

/**
 * Entry-point boostrap data.
 */
export type QLiveBoostrap = {
    /**
     * System config
     */
    config: QLiveConfig | null;
    /**
     * CSRF-Token needed to POST stuff
     */
    csrfToken: CSRFToken
    /**
     * The current user
      */
    authentication: Authentication
    /**
     * Injection data
     */
    data: {
        [key: string]: InjectionSource;
    }
}
/**
 * Bootstrap data a view runs on without a server, see initFixture().
 *
 * A bootstrap like any other but for its query document injections, which hold every row instead of a page of them:
 * `rows` is the whole result, `rowCount` its length, and `config` the one the view injected with, so the view opens
 * on the page it opens on live.
 */
export type QLiveFixture = QLiveBoostrap & {
    /**
     * What the fixture holds and where it came from, for people: the recorder gives the time, location, login and
     * rows it recorded; a fixture written by hand or generated says what it is made of.
     */
    description: string
    /**
     * Route of the view the fixture was recorded on, as routeOf() gives it: "grid/sorting". The injection ids in
     * `data` start with it, and FixtureScope renders its view at it. A route rather than the location path, which
     * only resolves under the base of the application it was recorded in.
     */
    route: string
}

export type QLiveConfig = {
    /**
     * Relative path of the QLive server
     */
    contextPath: string;
    /**
     * GraphQL schema for this application
     */
    schema: GraphQLSchema
    /**
     * Domain meta information for the schema.
     * Contents vary with MetadataProvider configuration. QLive comes with `maxPageSize` and query configuration by type
     * which is transmitted here.
     */
    meta: DomainMeta,

    // client-side only
    csrfToken?: CSRFToken

    /**
     * Who is looking at this page. Not part of the config the server renders -- that one is per module and
     * shared by everyone it is served to -- but put here on arrival, the way the CSRF token is, because
     * this is where an application looks things up.
     */
    authentication?: Authentication

    /**
     * Cached set of the names of QueryDocument<T> equivalent types in the application
     */
    queryDocumentTypes?: Set<string>
    /**
     * Cached lookup for GraphQL types by name
     */
    typesByName?: Map<string, GraphQLType>

    /**
     * The component rendered in place of a view QLive could not produce -- a path no view answers, a view
     * module that failed to load.
     *
     * The one member of this config an application writes rather than reads. An initialized config always
     * carries one, so a caller can render it without a fallback of its own; assign your own in startup()'s
     * init hook, which runs after the config is initialized and before anything is rendered.
     *
     *     await startup({
     *         views: import.meta.glob("./app/**\/*.tsx"),
     *         init: async config => {
     *             config.errorView = MyErrorPage
     *         }
     *     })
     */
    errorView?: ComponentType<ErrorViewProps>

    /**
     * What i18n() looks its tags up in: a tag, `"Filter {0}"` or `"Foo.name"`, to its translation, which holds the
     * same placeholders. A tag without one renders as itself in brackets, so a missing translation shows. Written by
     * the application like errorView, in startup()'s init hook.
     *
     *     config.translations = {"Foo.name": "Name", "Filter {0}": "{0} filtern"}
     */
    translations?: Record<string, string>
}

/**
 * Domain meta information from the server.
 *
 * Declared as an interface, not a type alias, so that an application can extend it. The server-side
 * io.github.qlivedev.graphql.meta.DomainMeta is an open map that every MetadataProvider bean adds its own addenda to,
 * and declaration merging is the client-side equivalent: name the addenda your providers write once and they are
 * typed at every place the application reads config().meta.
 *
 *     declare module "@qlivedev/qlive-ts" {
 *         interface DomainMeta {
 *             myAddendum: MyAddendumInfo[]
 *         }
 *     }
 *
 * @see DomainTypeMetaProps and DomainFieldMeta for the per-type and per-field levels, which extend the same way
 */
export interface DomainMeta {

    /**
     * Contains type names mapped to TypeMeta
     */
    types: {
        [typeName: string]: DomainTypeMeta
    }
    genericTypes: GenericTypeInfo[]
    relations: RelationInfo[]
    manyToMany: ManyToManyInfo[]
}

/**
 * Meta data for a single GraphQL object type.
 */
export interface DomainTypeMeta {

    /**
     * Field meta data by field name. Absent if no provider wrote field meta data for this type.
     */
    fields?: {
        [fieldName: string]: DomainFieldMeta
    }

    /**
     * Type meta data. Absent if no provider wrote type meta data for this type.
     */
    meta?: DomainTypeMetaProps
}

/**
 * One unique constraint of a table-backed domain type.
 */
export interface UniqueKeyInfo {

    /**
     * Name of the constraint in the database.
     */
    name: string

    /**
     * Fields of the type the constraint covers, in constraint order.
     */
    fields: string[]

    /**
     * Whether this is the primary key.
     */
    primary: boolean

    /**
     * Whether any of the fields is nullable. The database lets any number of rows hold NULL in a unique constraint, so
     * such a constraint does not make rows distinct -- a sort covering it can still have ties.
     */
    nullable: boolean
}

/**
 * Type-level meta data properties, written server-side with DomainTypeMeta#setMeta.
 *
 * Extend by declaration merging, see DomainMeta.
 */
export interface DomainTypeMetaProps {

    /**
     * Names of the fields naming an instance of the type to a user, most significant first. Written by QLive's
     * NameFieldProvider.
     *
     * A grid column of a relation and pick() show a row by these, so under `vite dev` a query selecting the type
     * without all of them logs a warning the first time it is used. Mutations are not checked.
     */
    nameFields?: string[]

    /**
     * The unique constraints of a table-backed type, the primary key first. Written by QLive's UniqueKeyProvider from
     * the jOOQ tables; unique indexes that are not constraints are not included.
     */
    uniqueKeys?: UniqueKeyInfo[]

    /**
     * What querying rows of this type looks like when nothing says otherwise: a page size, a sort order, a condition
     * every query of the type carries. A delta, so a type naming only a page size leaves the rest at the defaults a
     * query config has anyway.
     *
     * Written server-side by QLive's QueryConfigMetadataProvider, which an application registers as a MetadataProvider
     * bean if it wants type-level defaults at all -- absent everywhere in one that does not.
     *
     * The server applies this to the queries a view injects with useInjection(), whose parameters are static and have
     * no config to update. Nothing on the client applies it: read it where you build a query config from scratch and
     * want it to start where the injected ones start.
     */
    queryConfig?: QueryConfigDelta

    /**
     * The largest page any query over rows of this type comes back with. Absent where the type sets no limit, which is
     * also what a page size of 0 asks for.
     *
     * Written server-side by QLive's QueryConfigMetadataProvider and enforced there: a config asking for a larger page
     * -- or for all rows -- is held to this, and the config that comes back on the document says so. Read it to keep a
     * page size control from offering what the server will not give, not to enforce anything.
     */
    maxPageSize?: number

    /**
     * What this type declares about merging it: whether a conflict comes back to the view to resolve, which fields
     * never count as one, whether a non-overlapping change may merge silently, and whether the type is a link table.
     *
     * Written server-side by QLive's MergeMetadataProvider, which an application registers as a MetadataProvider bean
     * if it declares any of this -- absent everywhere in one that does not, and on every type that declared nothing.
     *
     * Whether a type takes part in merging at all is *not* in here: that is the type having a "version" field, which
     * both ends derive from the schema. Read it through the functions in merge/meta rather than off the map, so that
     * "declared nothing" and "no such type" answer the same way.
     */
    merge?: MergeTypeMeta
}

/**
 * Field-level meta data properties, written server-side with DomainTypeMeta#setFieldMeta.
 *
 * Extend by declaration merging, see DomainMeta.
 */
export interface DomainFieldMeta {

    /**
     * true if the field is a GraphQLComputed-annotated property. Written by QLive's ComputedMetadataProvider.
     */
    computed?: boolean

    /**
     * The most characters a string field holds -- or elements a list field, or entries a map field. Written by
     * QLive's SizeMetadataProvider from the field's `@Size(max = n)`, which jOOQ generates from the column length, so
     * a table-backed field of limited length has it. Absent where nothing limits the field.
     */
    maxLength?: number

    /**
     * The fewest characters a string field holds -- or elements a list field, or entries a map field. Written by
     * QLive's SizeMetadataProvider from the field's `@Size(min = n)`. jOOQ never generates a minimum, so only a field
     * that was given one by hand has it.
     */
    minLength?: number

    /**
     * The most significant digits a BigDecimal or BigInteger field holds, for a BigDecimal on both sides of the point
     * together. Written by QLive's DecimalMetadataProvider from the field's `@Column(precision = p)`, which jOOQ
     * generates for a `numeric(p, s)` column. Absent for an unconstrained `numeric`, and on every field that is
     * neither.
     */
    precision?: number

    /**
     * The digits a BigDecimal field holds after the point. Written by QLive's DecimalMetadataProvider from the field's
     * `@Column(scale = s)`, wherever it writes a precision -- 0 then means the field holds integers. Never on a
     * BigInteger, whose scale is 0 by definition.
     */
    scale?: number
}


let theConfig: QLiveConfig | null = null

/**
 * Whether QLive was initialized from a fixture rather than from a server.
 */
let onFixture = false

/**
 * Whether QLive was initialized at all, by init() or a fixture.
 */
let initialized = false

function logObject(label : string, data: {[key : string] : any}, logger : ((data: {[key : string] : any}, key: string) => void)): void
{
    console.groupCollapsed(label)
    const keys = Object.keys(data)

    for (let i = 0; i < keys.length; i++)
    {
        const key = keys[i];
        logger(data, key)
    }
    console.groupEnd()
}

const notLogged = new Set([
    // is derived and can be looked up in meta.genericTypes
    "queryDocumentTypes",
    // is derived and just another way of looking at schema.types
    "typesByName",
    "csrfToken"
])

function initializeDerivedConfig(theConfig: QLiveConfig)
{
    theConfig.typesByName = new Map<string, GraphQLType>(
        theConfig.schema.types.map(t => [t.name, t])
    )

    theConfig.queryDocumentTypes = new Set<string>(
        theConfig.meta.genericTypes
            .filter(gt => gt.genericType === "io.github.qlivedev.model.QueryDocument")
            .map(gt => gt.type)
    )
}

/**
 * Derives what the rest of QLive reads off the given config, which is the current one.
 */
function useConfig(config: QLiveConfig)
{
    initializeDerivedConfig(config);

    // the converters for the QueryDocument derived types come out of the config,
    // and inject() converts with them, so they have to be there before any view reads
    initConverters()
}

export function init(bs : QLiveBoostrap): Promise<QLiveConfig>
{
    const { config, data, csrfToken, authentication } = bs

    onFixture = false
    initialized = true
    theConfig = config
    if (theConfig)
    {
        theConfig.csrfToken = csrfToken
        theConfig.authentication = authentication
        // Before the init hook the application may replace it in, so that assigning is all it takes and
        // every reader can count on finding one.
        theConfig.errorView = DefaultErrorView

        useConfig(theConfig)
    }
    initData(data)

    return Promise.resolve(theConfig!)
}

/**
 * Initializes QLive from the given fixture, the way init() does from the bootstrap a server sends, and runs it on
 * that fixture from then on.
 *
 * A view running on a fixture runs unchanged. Its injections come out of the fixture, and its query documents answer
 * update() in the browser: they filter, sort and page the rows the fixture holds, as localDocument() does. Nothing
 * else goes anywhere -- graphql() rejects, so nothing is written, and subscribeToTopic() subscribes to nothing, so
 * nothing is pushed.
 *
 * This is for whatever renders a view without startup(): a Storybook decorator, a component test, an island on a
 * static page. startup({fixture}) runs a whole application on one.
 *
 *     initFixture(fixture)
 *     createRoot(element).render(<FooList/>)
 *
 * The config and the injections are module state: initializing another fixture replaces the injections of the last.
 * To run several views on fixtures on one page, add each with addFixture(), or render each in a FixtureScope.
 *
 * @param fixture   the fixture, e.g. one recorded from a running application in dev mode
 *
 * @returns the initialized config
 */
export function initFixture(fixture: QLiveFixture): Promise<QLiveConfig>
{
    const initialized = init(fixture)
    onFixture = true
    return initialized
}

/**
 * Adds the given fixture to those the page runs on, for several views on fixtures on one page: a docs page with
 * several demos, a canvas with several stories. FixtureScope calls it; this is for code that sets up a page without
 * React.
 *
 * The fixture's injections go in next to those already loaded. Injection ids carry the route of their view, so the
 * fixtures of different views don't clash. Where QLive isn't initialized yet, this initializes it as initFixture()
 * does. Otherwise the fixture has to fit the page:
 *
 *  - The page has to run on fixtures. graphql() and subscribeToTopic() ask isFixture(), which holds for the whole
 *    page, so a page can't be live in one place and on a fixture in another.
 *  - Its schema has to have the same types, or its data would convert by the wrong one without any sign of it.
 *    A reduced config, one without the schema, fits any; where one fixture has the full config and another the
 *    reduced one, the full one is kept.
 *  - An injection id already loaded has to come with the same data. Two fixtures for one route can't share a page.
 *
 * The authentication and CSRF token stay those of the fixture that initialized the config.
 *
 * @param fixture   the fixture
 *
 * @returns the config the page runs with
 */
export function addFixture(fixture: QLiveFixture): Promise<QLiveConfig>
{
    if (!initialized)
    {
        return initFixture(fixture)
    }

    if (!onFixture)
    {
        throw new Error(
            "Can't add the fixture for '" + fixture.route + "' to a page that runs on a server: graphql() and " +
            "subscribeToTopic() ask isFixture(), which holds for the whole page."
        )
    }

    const added = fixture.config
    if (added && !isReduced(added))
    {
        if (!theConfig || isReduced(theConfig))
        {
            added.csrfToken = theConfig?.csrfToken ?? fixture.csrfToken
            added.authentication = theConfig?.authentication ?? fixture.authentication
            added.errorView = theConfig?.errorView ?? DefaultErrorView
            theConfig = added
            useConfig(theConfig)
        }
        else
        {
            const differs = firstDifferentType(theConfig, added)
            if (differs)
            {
                throw new Error(
                    "The fixture for '" + fixture.route + "' comes with another schema than the page runs on: " +
                    "type '" + differs + "' is only in one of them. Fixtures sharing a page have to come from " +
                    "the same application."
                )
            }
        }
    }

    addData(fixture.data)

    return Promise.resolve(theConfig!)
}

/**
 * Whether the given config is a reduced one, i.e. one without the domain schema, see noSchema().
 */
function isReduced(config: QLiveConfig): boolean
{
    return !config.schema?.types?.length
}

/**
 * The first type name only one of the given configs' schemas has, sorted by name, or null where they have the same.
 */
function firstDifferentType(a: QLiveConfig, b: QLiveConfig): string | null
{
    const aNames = new Set(a.schema.types.map(t => t.name))
    const bNames = new Set(b.schema.types.map(t => t.name))

    const onlyInOne = [
        ...[...aNames].filter(name => !bNames.has(name)),
        ...[...bNames].filter(name => !aNames.has(name))
    ].sort()

    return onlyInOne.length ? onlyInOne[0] : null
}

/**
 * Returns true if QLive runs on a fixture, see initFixture().
 *
 * A view runs the same either way and normally has no reason to ask. Where it shows something only a server makes
 * work -- a save button, say -- this is how it can tell.
 */
export function isFixture(): boolean
{
    return onFixture
}

/**
 * Logs the config and the injections the page started with.
 *
 * Called by startup() once its init hook has run, not by init() itself: the hook sits between the two and is
 * where an application replaces the errorView, so logging any earlier would report a config no page ever
 * runs with.
 */
export function logStartup(bs : QLiveBoostrap)
{
    if (theConfig)
    {
        logObject(
            "CONFIG",
            theConfig as {[key : string] : any},
            (data: {[key : string] : any}, key: string) : void => {

                if (key === "errorView")
                {
                    console.log(key, "<" + (theConfig!.errorView?.name || "Custom") + "/>")
                }
                else if (!notLogged.has(key))
                {
                    console.log(key, data[key])
                }
            }
        );
    }

    logObject(
        "INJECTED",
        bs.data,
        (data, key) => console.log(key, data[key].data, "( type = \"" + data[key].type+ "\" )", "meta = ", data[key].meta),
    );
}

/**
 * Returns the QLive config for the current application.
 */
/**
 * The config, or null before init().
 *
 * @internal
 */
export function currentConfig(): QLiveConfig | null
{
    return theConfig
}

export default function config(): QLiveConfig {
    if (!theConfig)
    {
        throw new Error("Config not initialized")
    }

    return theConfig
}
