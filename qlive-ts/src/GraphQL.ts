import { ConditionNode, FieldNode, RawValue } from "./FilterDSL"

type Scalar = boolean | number | string | bigint;

type GenericBigDecimal = {
    scalarType: "BigDecimal"
    value: bigint
}

type GenericByte = {
    scalarType: "Byte"
    value: number
}


type GenericComputedValue = {
    scalarType: "ComputedValue"
    value: {
        name: string,
        args: [ Scalar ]
    }
}


type GenericCondition = {
    scalarType: "Condition"
    value: ConditionNode
}


type GenericDate = {
    scalarType: "Date"
    value: string
}


type GenericDomainObject = {
    scalarType: "DomainObject"
    value: object
}


type GenericFieldExpression = {
    scalarType: "FieldExpression"
    value: string | FieldNode
}


type GenericJSONB = {
    scalarType: "JSONB"
    value: RawValue
}


type GenericLong = {
    scalarType: "Long"
    value: bigint
}


type GenericTimestamp = {
    scalarType: "Timestamp"
    value: string
}

/**
 * Generic scalar wrapper for GraphQL. 
 */
export type GenericScalar = GenericBigDecimal | GenericByte | GenericComputedValue | GenericCondition | GenericDate |
    GenericDomainObject | GenericFieldExpression | GenericJSONB | GenericLong | GenericTimestamp

