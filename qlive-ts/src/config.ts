import {GraphQLSchema, GraphQLType} from "./GraphQLSchema";
import {initData} from "./data";
import {initConverters} from "./converter";

/**
 * Meta-information about types that are generic types on the Java side.
 *
 * For example, com.dataciders.qlive.model.QueryDocument<T> is the generic Java class for query documents. In the GraphQL
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
    genericType: "com.dataciders.qlive.model.QueryDocument" | string
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

export type InjectionSource = {
    data: any,
    type: string,
    meta: any,
}

export type Injection = {
    value: any,
    type: string,
    meta: any
}

export type CSRFToken = {
    param: string
    header: string
    value: string
}

export type QLiveBoostrap = {
    config: QLiveConfig | null;
    csrfToken: CSRFToken
    data: {
        [key: string]: InjectionSource;
    }
}
export type QLiveConfig = {
    /**
     * Relative path of the QLive server
     */
    contextPath: string;
    schema: GraphQLSchema
    meta: DomainQLMeta,

    // client-side only
    csrfToken?: CSRFToken
    queryDocumentTypes?: Set<string>
    typesByName?: Map<string, GraphQLType>

}

/**
 * Domain meta information from DomainQL.
 *
 * Declared as an interface, not a type alias, so that an application can extend it. The server-side
 * de.quinscape.domainql.meta.DomainQLMeta is an open map that every MetadataProvider bean adds its own addenda to,
 * and declaration merging is the client-side equivalent: name the addenda your providers write once and they are
 * typed at every place the application reads config().meta.
 *
 *     declare module "@quinscape/qlive-ts" {
 *         interface DomainQLMeta {
 *             myAddendum: MyAddendumInfo[]
 *         }
 *     }
 *
 * @see DomainQLTypeMetaProps and DomainQLFieldMeta for the per-type and per-field levels, which extend the same way
 */
export interface DomainQLMeta {

    /**
     * Contains type names mapped to TypeMeta
     */
    types: {
        [typeName: string]: DomainQLTypeMeta
    }
    genericTypes: GenericTypeInfo[]
    relations: RelationInfo[]
}

/**
 * Meta data for a single GraphQL object type.
 */
export interface DomainQLTypeMeta {

    /**
     * Field meta data by field name. Absent if no provider wrote field meta data for this type.
     */
    fields?: {
        [fieldName: string]: DomainQLFieldMeta
    }

    /**
     * Type meta data. Absent if no provider wrote type meta data for this type.
     */
    meta?: DomainQLTypeMetaProps
}

/**
 * Type-level meta data properties, written server-side with DomainQLTypeMeta#setMeta.
 *
 * Extend by declaration merging, see DomainQLMeta.
 */
export interface DomainQLTypeMetaProps {

    /**
     * Names of the fields naming an instance of the type to a user, most significant first. Written by domainql's
     * NameFieldProvider.
     */
    nameFields?: string[]
}

/**
 * Field-level meta data properties, written server-side with DomainQLTypeMeta#setFieldMeta.
 *
 * Extend by declaration merging, see DomainQLMeta.
 */
export interface DomainQLFieldMeta {

    /**
     * true if the field is a GraphQLComputed-annotated property. Written by domainql's ComputedMetadataProvider,
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
            .filter(gt => gt.genericType === "com.dataciders.qlive.model.QueryDocument")
            .map(gt => gt.type)
    )
}

export function init(bs : QLiveBoostrap)
{
    const { config, data, csrfToken } = bs

    theConfig = config
    if (theConfig)
    {
        theConfig.csrfToken = csrfToken

        initializeDerivedConfig(theConfig);

        // the converters for the QueryDocument derived types come out of the config,
        // and initData() converts, so this has to happen in between
        initConverters()
    }
    initData(data)

    logObject(
        "CONFIG",
        config as {[key : string] : any},
        (data: {[key : string] : any}, key: string) : void => {
            if (!notLogged.has(key))
            {
                console.log(key, data[key])
            }
        }
    );
    logObject(
        "INJECTED",
        data,
        (data, key) => console.log(key, data[key].data, "( type = \"" + data[key].type+ "\" )", "meta = ", data[key].meta),
    );

    return Promise.resolve()
}

export default function config(): QLiveConfig {
    if (!theConfig)
    {
        throw new Error("Config not initialized")
    }

    return theConfig
}
