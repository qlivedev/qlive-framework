import type {ComponentType} from "react";

import type {QueryConfigDelta} from "./QueryDocument";
import type {MergeTypeMeta} from "./merge/meta";

import {GraphQLSchema, GraphQLType} from "./GraphQLSchema";
import {initData} from "./data";
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
 * Type-level meta data properties, written server-side with DomainTypeMeta#setMeta.
 *
 * Extend by declaration merging, see DomainMeta.
 */
export interface DomainTypeMetaProps {

    /**
     * Names of the fields naming an instance of the type to a user, most significant first. Written by QLive's
     * NameFieldProvider.
     */
    nameFields?: string[]

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
     * true if the field is a GraphQLComputed-annotated property. Written by QLive's ComputedMetadataProvider,
     * which an application has to register as a MetadataProvider bean itself.
     */
    computed?: boolean
}


let theConfig: QLiveConfig | null = null

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

export function init(bs : QLiveBoostrap): Promise<QLiveConfig>
{
    const { config, data, csrfToken, authentication } = bs

    theConfig = config
    if (theConfig)
    {
        theConfig.csrfToken = csrfToken
        theConfig.authentication = authentication
        // Before the init hook the application may replace it in, so that assigning is all it takes and
        // every reader can count on finding one.
        theConfig.errorView = DefaultErrorView

        initializeDerivedConfig(theConfig);

        // the converters for the QueryDocument derived types come out of the config,
        // and initData() converts, so this has to happen in between
        initConverters()
    }
    initData(data)

    return Promise.resolve(theConfig!)
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

export default function config(): QLiveConfig {
    if (!theConfig)
    {
        throw new Error("Config not initialized")
    }

    return theConfig
}
