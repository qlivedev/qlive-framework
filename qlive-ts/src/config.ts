import {GraphQLSchema} from "./GraphQLSchema";
import {initData} from "./data";

/**
 * Meta-information about types that are generic types on the Java side.
 *
 * For example, de.quinscape.qlive.model.QueryDocument<T> is the generic Java class for query documents. In the GraphQL
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
    genericType: "de.quinscape.qlive.model.QueryDocument" | string
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

type SourceField =
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

type TargetField =
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
    meta: any
}

export type Injection = {
    value: any,
    type: string,
    meta: any
}

export type QLiveBoostrap = {
    config: QLiveConfig | null;
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
    meta: DomainQLMeta
}

/**
 * Domain meta information from DomainQL
 */
export type DomainQLMeta = {

    /**
     * Contains type names mapped to TypeMeta
     */
    types: {
        [typeName: string]: DomainQLTypeMeta
    }
    genericTypes: GenericTypeInfo[]
    relations: RelationInfo[]
}

export type DomainQLTypeMeta = {
    fields: {
        [fieldName: string]: {

        }
    }
    meta?: {
        nameFields?: string
    }
}


let theConfig: QLiveConfig | null = null

function logObject(label : string, data: {[key : string] : any})
{
    console.groupCollapsed(label)
    const keys = Object.keys(data)
    for (let i = 0; i < keys.length; i++)
    {
        const key = keys[i];
        console.log(key, " =", data[key])
    }
    console.groupEnd()
}

export function init(bs : QLiveBoostrap)
{
    const { config, data } = bs

    theConfig = config
    initData(data)

    logObject("CONFIG", config as {[key : string] : any});
    logObject("INJECTED", data);

    return Promise.resolve()
}

export default function config(): QLiveConfig {
    if (!theConfig)
    {
        throw new Error("Config not initialized")
    }

    return theConfig
}
