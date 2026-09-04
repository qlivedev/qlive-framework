import {ConditionNode, FieldNode, RawValue} from "./FilterDSL"

type Scalar = boolean | number | string | bigint;

export type GenericBigDecimal = {
    scalarType: "BigDecimal"
    value: bigint
}

export type GenericByte = {
    scalarType: "Byte"
    value: number
}


export type GenericComputedValue = {
    scalarType: "ComputedValue"
    value: {
        name: string,
        args: [Scalar]
    }
}


export type GenericCondition = {
    scalarType: "Condition"
    value: ConditionNode
}


export type GenericDate = {
    scalarType: "Date"
    value: string
}


export type GenericDomainObject = {
    scalarType: "DomainObject"
    value: object
}


export type GenericFieldExpression = {
    scalarType: "FieldExpression"
    value: string | FieldNode
}


export type GenericJSONB = {
    scalarType: "JSONB"
    value: RawValue
}


export type GenericLong = {
    scalarType: "Long"
    value: bigint
}


export type GenericTimestamp = {
    scalarType: "Timestamp"
    value: string
}

/**
 * Generic scalar wrapper for GraphQL.
 */
export type GenericScalar = GenericBigDecimal | GenericByte | GenericComputedValue | GenericCondition | GenericDate |
    GenericDomainObject | GenericFieldExpression | GenericJSONB | GenericLong | GenericTimestamp

