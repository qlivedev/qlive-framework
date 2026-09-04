/*
 * Types for the GraphQL introspection data the server embeds into the QLive
 * config. The server strips the outer envelope, so the root type here is the
 * *value* of the "__schema" key, not the { __schema: ... } wrapper.
 *
 * Derived from the graphql-java side of things:
 *
 *  * which members exist per kind follows the graphql.schema type classes
 *    (GraphQLObjectType, GraphQLEnumType, ... ) and the way the introspection
 *    data fetchers in graphql.introspection.Introspection map them: a member
 *    is non-null exactly for the kinds whose Java class provides it, and null
 *    for all others.
 *  * which members are queried at all follows
 *    de.quinscape.domainql.util.IntrospectionUtil.INTROSPECTION_QUERY, which
 *    is what BootstrapService introspects with. It is narrower than the full
 *    introspection of the spec.
 */

/**
 * Kind of a named type, i.e. one of the graphql.schema.GraphQLNamedType
 * implementations.
 */
export type GraphQLNamedTypes =
    "SCALAR" |
    "OBJECT" |
    "INTERFACE" |
    "UNION" |
    "ENUM" |
    "INPUT_OBJECT"

/**
 * Kind of a type modifier, i.e. one of the graphql.schema.GraphQLModifiedType
 * implementations wrapping another type.
 */
export type GraphQLModifiedTypes =
    "LIST" |
    "NON_NULL"

export type GraphQLTypeKind = GraphQLNamedTypes | GraphQLModifiedTypes

/**
 * Reference to one of the named types in GraphQLSchema.types.
 */
export type GraphQLNamedTypeRef = {
    kind: GraphQLNamedTypes
    name: string
    /**
     * Named types wrap nothing. Missing on the innermost level (see
     * GraphQLModifiedTypeRef.ofType).
     */
    ofType?: null
}

/**
 * LIST modifier wrapping another type reference. Has no name of its own.
 */
export type GraphQLList = {
    kind: "LIST"
    name: null
    /**
     * The wrapped type.
     *
     * Missing if the modifier sits on the innermost of the four nesting levels
     * the introspection query selects. Only reachable with more than four
     * modifiers on one type, e.g. [[String!]!]!
     */
    ofType?: GraphQLTypeRef
}

/**
 * NON_NULL modifier wrapping another type reference. Has no name of its own.
 */
export type GraphQLNonNull = {
    kind: "NON_NULL"
    name: null
    /**
     * The wrapped type. Missing on the innermost nesting level, see
     * GraphQLListTypeRef.ofType
     */
    ofType?: GraphQLTypeRef
}

/**
 * One of the two type modifiers (graphql.schema.GraphQLModifiedType)
 */
export type GraphQLModifiedTypeRef = GraphQLList | GraphQLNonNull

/**
 * Reference to the type of a field, argument or input field, i.e. a named type
 * potentially wrapped in LIST / NON_NULL modifiers.
 */
export type GraphQLTypeRef = GraphQLNamedTypeRef | GraphQLModifiedTypeRef

/**
 * Argument of a field (graphql.schema.GraphQLArgument) or field of an input
 * object (graphql.schema.GraphQLInputObjectField). Both are introspected as
 * __InputValue.
 */
export type GraphQLInputValue = {
    name: string
    description: string | null
    type: GraphQLTypeRef
    /**
     * Default value as GraphQL literal source, null if there is none
     */
    defaultValue: string | null
}

/**
 * Field of an object or interface type (graphql.schema.GraphQLFieldDefinition)
 */
export type GraphQLField = {
    name: string
    description: string | null
    args: GraphQLInputValue[]
    type: GraphQLTypeRef
    isDeprecated: boolean
    deprecationReason: string | null
}

/**
 * Value of an enum type (graphql.schema.GraphQLEnumValueDefinition)
 */
export type GraphQLEnumValue = {
    name: string
    description: string | null
    isDeprecated: boolean
    deprecationReason: string | null
}

export type GraphQLNamedTypeBase = {
    name: string
    description: string | null
}

/**
 * Scalar type (graphql.schema.GraphQLScalarType). Has none of the kind
 * specific members.
 */
export type GraphQLScalarType = GraphQLNamedTypeBase & {
    kind: "SCALAR"
    fields: null
    inputFields: null
    interfaces: null
    enumValues: null
    possibleTypes: null
}

/**
 * Object type (graphql.schema.GraphQLObjectType)
 */
export type GraphQLObjectType = GraphQLNamedTypeBase & {
    kind: "OBJECT"
    /**
     * Fields of the object, deprecated ones included
     */
    fields: GraphQLField[]
    inputFields: null
    /**
     * Interfaces implemented by this object
     */
    interfaces: GraphQLNamedTypeRef[]
    enumValues: null
    possibleTypes: null
}

/**
 * Interface type (graphql.schema.GraphQLInterfaceType)
 */
export type GraphQLInterfaceType = GraphQLNamedTypeBase & {
    kind: "INTERFACE"
    /**
     * Fields of the interface, deprecated ones included
     */
    fields: GraphQLField[]
    inputFields: null
    /**
     * Interfaces implemented by this interface
     */
    interfaces: GraphQLNamedTypeRef[]
    enumValues: null
    /**
     * Object types implementing this interface
     */
    possibleTypes: GraphQLNamedTypeRef[]
}

/**
 * Union type (graphql.schema.GraphQLUnionType)
 */
export type GraphQLUnionType = GraphQLNamedTypeBase & {
    kind: "UNION"
    fields: null
    inputFields: null
    interfaces: null
    enumValues: null
    /**
     * Object types that are part of this union
     */
    possibleTypes: GraphQLNamedTypeRef[]
}

/**
 * Enum type (graphql.schema.GraphQLEnumType)
 */
export type GraphQLEnumType = GraphQLNamedTypeBase & {
    kind: "ENUM"
    fields: null
    inputFields: null
    interfaces: null
    /**
     * Values of the enum, deprecated ones included
     */
    enumValues: GraphQLEnumValue[]
    possibleTypes: null
}

/**
 * Input object type (graphql.schema.GraphQLInputObjectType)
 */
export type GraphQLInputObjectType = GraphQLNamedTypeBase & {
    kind: "INPUT_OBJECT"
    fields: null
    /**
     * Fields of the input object
     */
    inputFields: GraphQLInputValue[]
    interfaces: null
    enumValues: null
    possibleTypes: null
}

/**
 * One of the named types of the schema, discriminated by "kind". The members
 * not applying to a kind are present as null, so they can be accessed without
 * narrowing first:
 *
 * ```ts
 *  // GraphQLField[] | null
 *  const fields = type.fields
 *
 *  if (type.kind === "OBJECT")
 *  {
 *      // GraphQLField[]
 *      const fields = type.fields
 *  }
 * ```
 */
export type GraphQLType =
    GraphQLScalarType |
    GraphQLObjectType |
    GraphQLInterfaceType |
    GraphQLUnionType |
    GraphQLEnumType |
    GraphQLInputObjectType

/**
 * GraphQL introspection data (__Schema), starting with the value of the
 * "__schema" key.
 */
export type GraphQLSchema = {
    /**
     * All types of the schema, including the introspection types themselves
     */
    types: GraphQLType[]
}
