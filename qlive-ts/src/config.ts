/**
 * Meta-information about types that are generic types on the Java side.
 *
 * For example, de.quinscape.qlive.model.QueryDocument<T> is the generic Java class for query documents. In the GraphQL
 * world, the same type is called FooDocument with Foo replacing the generic T.
 */
type GenericTypeInfo = {
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
type RelationInfo = {


    /**
     * Right side type of the relation
     */
    targetType: string,
    leftSideObjectName: "fooType",

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
    id: string,

    /**
     * Fields on the left side table forming the foreign key
     */
    sourceFields: string[],

    /**
     * Fields on the right side table the foreign key is pointing to
     */
    targetFields: ["name"]
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

export type QLiveConfig = {
    /**
     * Relative path of the QLive server
     */
    contextPath: string;
    meta: DomainQLMeta
}

type DomainQLMeta = {
    types: {
        [typeName: string]: {
            meta?: {
                nameFields: string
            }
        }
    },
    genericTypes: Array<GenericTypeInfo>,
    relations: Array<RelationInfo>
}


let theConfig: QLiveConfig | null = null

export function init(config: QLiveConfig)
{
    theConfig = config

    console.log("INIT ", config)
}

export default function config(): QLiveConfig {
    if (!theConfig)
    {
        throw new Error("Config not initialized")
    }

    return theConfig
}
