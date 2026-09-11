import {ConditionNode, FieldNode, RawValue} from "./FilterDSL"
import {Temporal} from "temporal-polyfill";

type Scalar = boolean | number | string | bigint;

/**
 * A value of any scalar type the domain has, traveling as the name of that type plus a value of it.
 *
 * That is what lets one GraphQL field accept every scalar the application has: the server coerces the value
 * along the named type, so a mutation taking a GenericScalar needs no input type per domain type. QLive's
 * merge mutation is the reason it exists here -- a field change is a field name and one of these.
 *
 * The type name is open on purpose. Which scalars there are is the domain's decision, so "String", "Int" and
 * whatever the application registered are as valid here as the ones below, and a closed union would exclude
 * most of what is actually sent. The aliases below narrow this for the types QLive knows, and an application
 * that knows what it is holding can name one of them.
 */
export type GenericScalar = {

    /**
     * Name of the scalar type the value is of, e.g. "Timestamp". A list of them is written the way GraphQL
     * writes it, e.g. "[String]".
     */
    type: string

    /**
     * The value, in the live form of its type -- a Timestamp is a Temporal.Instant here and an ISO-8601
     * string on the wire, the same as everywhere else in QLive.
     */
    value: any
}

export type GenericBigDecimal = {
    type: "BigDecimal"
    value: bigint
}

export type GenericByte = {
    type: "Byte"
    value: number
}


export type GenericComputedValue = {
    type: "ComputedValue"
    value: {
        name: string,
        args: [Scalar]
    }
}


export type GenericCondition = {
    type: "Condition"
    value: ConditionNode
}


export type GenericDate = {
    type: "Date"
    value: string
}


export type GenericDomainObject = {
    type: "DomainObject"
    value: object
}


export type GenericFieldExpression = {
    type: "FieldExpression"
    value: string | FieldNode
}


export type GenericJSONB = {
    type: "JSONB"
    value: RawValue
}


export type GenericLong = {
    type: "Long"
    value: bigint
}


export type GenericTimestamp = {
    type: "Timestamp"
    value: Temporal.Instant
}
