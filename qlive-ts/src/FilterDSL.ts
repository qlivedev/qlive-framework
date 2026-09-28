import {Temporal} from "temporal-polyfill";

/**
 * Field reference.
 */
export interface FieldNode
{
    type: "Field",
    /**
     * Name of the field
     */
    name: string
}

/**
 * Named condition Node.
 */
export interface ConditionNode
{
    type: "Condition",
    /**
     * Name of the condition (e.g. "greater")
     */
    name: string,
    /**
     * Operands of the condition
     */
    operands: CNode[]
}

/**
 * JSON value equivalent
 */
export type RawValue = null | string | number | boolean | RawValue[] | { [key: string]: RawValue };

/**
 * Value node. Represents a single runtime-typed value.
 */
export interface ValueNode
{
    type: "Value",
    /**
     * Scalar type of the value
     */
    scalarType: string,
    /**
     * Raw/json value
     */
    value: RawValue
}

/**
 * Values node. Represents an array of values.
 */
export interface ValuesNode
{
    type: "Values",
    /**
     * Scalar type of the elements in the value array
     */
    scalarType: string,
    /**
     * Array of raw values.
     */
    values: RawValue[]
}

/**
 * Operation node. Addition, subtraction etc.
 */
export interface OperationNode
{
    type: "Operation",
    /**
     * Name of the operation
     */
    name: string,
    /**
     * Operands of the operation
     */
    operands: CNode[]
}

/**
 * Component node. Wraps another condition. Think of it like a named term.
 */
export interface ComponentNode
{
    type: "Component",
    /**
     * Id of the component
     */
    id: string | null,
    /**
     * Actual condition, or `null` for a component that currently filters nothing. An empty component keeps its
     * place in a composition; only the SQL transformation drops it.
     */
    condition: CNode | null
}


export type CNode = FieldNode | ConditionNode | ValueNode | ValuesNode | OperationNode | ComponentNode;

/**
 * A composed filter: a condition, or a component marker wrapping one. This is
 * what a filter *is* - it never includes falsy members.
 */
export type FilterExpression = ConditionNode | ComponentNode;

/**
 * What or() / and() *accept*. Falsy operands are dropped, which is the point of
 * the logical composers: callers compose from helpers that may contribute
 * nothing and let the final logical shape fall out of whatever survived.
 *
 * Note that `flag && cond` only lands in this type when `flag` is a boolean.
 * For a truthy-narrowable value use `!!flag && cond`, or the equivalent
 * `flag ? cond : null` - `"" | undefined | Condition` is not an operand.
 */
export type LogicalOperand = FilterExpression | null | undefined | false;

export type FieldExpression = string | CNode

const validTypeValues = [
    "Field",
    "Condition",
    "Value",
    "Values",
    "Operation",
    "Component"
]

export type Typed = { type: string };

/**
 * Returns true if the given value is a condition node.
 *
 * @param value
 */
export function isCondition(value: Typed): value is CNode
{
    if (!isConditionObject(value))
        return false;

    return validTypeValues.indexOf(value.type) >= 0;
}

/**
 * Logical not condition.
 *
 *
 * @param {CNode} operand
 * @return {CNode} negated condition
 */
export function not(operand: CNode): CNode
{
    if (isConditionObject(operand))
    {
        const cond = new Condition("not");
        cond.operands = [operand];
        return cond;
    }

    const cond = new Condition("false");
    cond.operands = [];
    return cond;
}

/**
 * Logical or condition. Will ignore falsy operands. An empty or collapses to null.
 *
 * @param {... CNode} operands
 * @return {CNode} ORed condition
 */
export const or: (...args: LogicalOperand[]) => (FilterExpression | null) = buildLogical("or");
/**
 * Logical and condition. Will ignore falsy operands. An empty and collapses to null.
 *
 * @param {... CNode} operands
 * @return {CNode} ANDed condition
 */
export const and: (...args: LogicalOperand[]) => (FilterExpression | null) = buildLogical("and");

function buildLogical(name: string): (...args: LogicalOperand[]) => FilterExpression | null
{
    return function (...args: LogicalOperand[]): FilterExpression | null {

        const operands: FilterExpression[] = [];

        const len = args.length

        for (let i = 0; i < len; i++)
        {
            const condition = args[i];
            if (isConditionObject(condition))
            {
                // isConditionObject is a truthy/object check, looser than the
                // type - it cannot tell a condition from any other node.
                operands.push(condition as FilterExpression);
            }
        }

        if (operands.length === 0)
        {
            return null;
        }
        if (operands.length === 1)
        {
            return operands[0];
        }

        const cond = new Condition(name);
        cond.operands = operands;
        return cond;
    }
}

/**
 * Builder factory for conditions
 * @param name      condition name
 * @param numArgs   number of arguments for the condition
 */
function buildFn(name: string, numArgs: number): (...args: CNode[]) => CNode
{
    return function (this: CNode, ...args: CNode[]): CNode {
        const cond = new Condition(name);
        cond.operands = [this, ...args.slice(0, numArgs)];
        return cond;
    }
}

/**
 * Builder factory for operations
 * @param name      condition name
 * @param numArgs   number of arguments for the condition
 */
function buildOpFn(name: string, numArgs: number): (...args: CNode[]) => CNode
{
    return function (this: CNode, ...args: CNode[]): CNode {
        // Operations reuse the Field prototype - identical method set, different
        // node type - so the node is constructed as a Field and relabelled.
        const op = new FieldCtor(name) as unknown as OperationNode;
        op.type = "Operation";
        op.operands = [this, ...args.slice(0, numArgs)];
        return op;
    }
}

const FIELD_CONDITIONS = {
    "greaterOrEqual": 1,
    "lessOrEqual": 1,
    "lt": 1,
    "notBetweenSymmetric": 2,
    "notEqualIgnoreCase": 1,
    "betweenSymmetric": 2,
    "lessThan": 1,
    "equalIgnoreCase": 1,
    "isDistinctFrom": 1,
    "between": 2,
    "ge": 1,
    "greaterThan": 1,
    "isNotNull": 0,
    "notLikeRegex": 1,
    "notBetween": 2,
    "notEqual": 1,
    "isFalse": 0,
    "containsIgnoreCase": 1,
    "eq": 1,
    "gt": 1,
    "equal": 1,
    "likeRegex": 1,
    "isTrue": 0,
    "contains": 1,
    "notContainsIgnoreCase": 1,
    "notContains": 1,
    "ne": 1,
    "isNull": 0,
    "endsWith": 1,
    "le": 1,
    "isNotDistinctFrom": 1,
    "startsWith": 1,
    // 1 collection arg
    "in": 1
} as const;

const CONDITION_METHODS = {
    "not": 0,
    "or": 1,
    "orNot": 1,
    "and": 1,
    "andNot": 1
} as const;

const FIELD_OPERATIONS = {
    "bitNand": 1,
    "mod": 1,
    "div": 1,
    "neg": 0,
    "rem": 1,
    "add": 1,
    "subtract": 1,
    "plus": 1,
    "bitAnd": 1,
    "bitXor": 1,
    "shl": 1,
    "unaryMinus": 0,
    "bitNor": 1,
    "shr": 1,
    "modulo": 1,
    "bitXNor": 1,
    "bitNot": 0,
    "sub": 1,
    "minus": 1,
    "mul": 1,
    "bitOr": 1,
    "times": 1,
    "pow": 1,
    "divide": 1,
    "power": 1,
    "multiply": 1,
    "unaryPlus": 0,
    "lower": 0,
    "upper": 0,
    "concat": 1,

    // toString is special and gets translated into a cast(String.class)
    "toString": 0,

    // for sort order fields
    "asc": 0,
    "desc": 0
} as const;

/**
 * Automatically creates a builder function for the given name and number of arguments. All conditions are the same, all
 * operations are the same and differ only in name and number of arguments they accept. Both group only differ in type
 * discriminator field.
 */
type FunctionFactory = (name: string, numArgs: number) => (...args: CNode[]) => CNode

/**
 * Enriches the given prototype with builder functions created from the given raw method map.
 *
 * @param proto         Prototype
 * @param methodsMap    Maps a condition or operation name to the number of arguments it accepts.
 * @param factory       factory function
 */
function buildProto(
    proto: Record<string, unknown>,
    methodsMap: Readonly<Record<string, number>>,
    factory: FunctionFactory
)
{
    for (let name in methodsMap)
    {
        if (methodsMap.hasOwnProperty(name))
        {
            const numArgs = methodsMap[name];

            proto[name] = factory(name, numArgs);
        }
    }
}

/**
 * Field constructor
 * @param name
 * @constructor
 */
function Field(this: FieldNode, name: string)
{
    this.type = "Field";
    this.name = name;
}

buildProto(Field.prototype, FIELD_CONDITIONS, buildFn);
buildProto(Field.prototype, FIELD_OPERATIONS, buildOpFn);

/*
 * buildProto() attaches the method set above at runtime, which TypeScript
 * cannot observe, and a plain function has no construct signature anyway.
 * These aliases are the single boundary where the metaprogramming is asserted
 * to the type system: they state the shape the prototype actually has, so
 * every `new` below - and every consumer - gets the fully typed node.
 */
const FieldCtor = Field as unknown as { new (name: string): Field };

/**
 * Condition constructor
 * @param name
 * @constructor
 */
function ConditionImpl(this: ConditionNode, name: string)
{
    this.type = "Condition";
    this.name = name;
}

buildProto(ConditionImpl.prototype, CONDITION_METHODS, buildFn);

/*
 * Exported as a value, so consumers can construct conditions directly. Same
 * assertion as FieldCtor above, and it is what gives the export the construct
 * signature `new FilterDSL.Condition(name)` needs to typecheck.
 */
export const Condition = ConditionImpl as unknown as { new (name: string): Condition };

export function isConditionObject(value: any): boolean
{
    return value && typeof value === "object";
}

/**
 * Generic condition node. Useful for programmatically instantiating conditions. Not needed for fluent style conditions.
 *
 * @param {String} name                     condition name
 * @param {Array<CNode>} operands   operands
 * @return {CNode}
 */
export function condition(name: string, operands: CNode[] = []): CNode
{
    const condition = new Condition(name);
    condition.operands = operands
    return condition;
}

/**
 * Generic operation node. Useful for programmatically instantiating conditions. Not needed for fluent style conditions.
 * @param name
 * @param operands
 */
export function operation(name: string, operands: CNode[] = []): CNode
{
    const op = new FieldCtor(name) as unknown as OperationNode;
    op.type = "Operation";
    op.operands = operands;
    return op;
}


/**
 * Field / column reference.
 *
 * @param {String} name     field name (e.g. "name", "owner.name")
 * @return {Field}
 */
export function field(name: string): Field
{
    return new FieldCtor(name);
}


/**
 * Component condition node. These nodes are just marker for which part of the condition originated from which component
 * Logically they are evaluated as the condition they wrap.
 *
 * @param {String} id                   component id
 * @param {CNode} condition     actual condition for the component, `null` for an empty one
 *
 * @return {CNode}
 */
export function component(id: string | null, condition: CNode | null): ComponentNode
{
    return {
        type: "Component",
        id,
        condition
    };
}

/**
 * Value node
 *
 * @param type      scalar type
 * @param value     raw value
 * @constructor
 */
function Value(this: ValueNode, type: string, value: RawValue)
{
    this.type = "Value";
    this.scalarType = type;
    this.value = value;
}

buildProto(Value.prototype, FIELD_CONDITIONS, buildFn);
buildProto(Value.prototype, FIELD_OPERATIONS, buildOpFn);

const ValueCtor = Value as unknown as { new (type: string, value: RawValue): Value };

/**
 * Values node.
 *
 * @param type      scalar type
 * @param values    raw value array
 * @constructor
 */
function Values(this: ValuesNode, type: string, values: RawValue[])
{
    this.type = "Values";
    this.scalarType = type;
    this.values = values;
}

const ValuesCtor = Values as unknown as { new (type: string, values: RawValue[]): ValuesNode };


/**
 * Returns the default scalar type for the given values. For many types this can just be concluded from the JS value type
 * for some
 * @param value
 */
function getDefaultType(value: any): string
{
    if (typeof value === "string")
    {
        return "String"
    } else if (typeof value === "number")
    {
        return "Int"
    } else if (typeof value === "boolean")
    {
        return "Boolean"
    } else if (value instanceof Temporal.Instant)
    {
        return "Timestamp"
    } else
    {
        throw new Error(
            "Could not determine scalar type for value: " + value + ".\n" +
            "Please define the correct scalar type as second argument to value()."
        )
    }
}


/**
 * Creates a new value node
 *
 * @param {Object} value    scalar value of appropriate type
 * @param {String} [type]   scalar type name if not given the type will be selected based on value type
 *
 * @return {ValueNode} value node
 */
export function value(value: RawValue, type: string = getDefaultType(value)): ValueNode
{
    return new ValueCtor(type, value);
}

/**
 * Creates a new values node that encapsulates a collection of scalar values (for e.g. the IN operator)
 *
 * @param {String} type     scalar type name
 * @param {Object} values   var args of scalar value of appropriate type
 *
 * @return {ValuesNode} values node
 */
export function values(type: string, ...values: RawValue[]): ValuesNode
{
    return new ValuesCtor(type, values);
}

/**
 * Returns the number of expected arguments for the condition with the given name.
 *
 * @param {String} name     condition name
 *
 * @return {number} number of value arguments expected
 */
export function getConditionArgCount(name: Function | string): number
{
    if (typeof name === "function")
    {
        return name.length - 1;
    }

    // Widened views of the const tables: `name` is an arbitrary string here,
    // not one of their literal keys. `||`, not `??`: a 0-arity entry in the
    // first table falls through to the second.
    const conditionMethods: Readonly<Record<string, number>> = CONDITION_METHODS;
    const fieldConditions: Readonly<Record<string, number>> = FIELD_CONDITIONS;

    const count = conditionMethods[name] || fieldConditions[name];

    return typeof count === "number" ? count : 1
}


/**
 * Returns true if the given condition node is either a logical and or a logical or condition.
 *
 * @param {Object} node     node
 * @return {boolean}    true if the node is either an "and" or an "or"
 */
export function isLogicalCondition(node: CNode): node is ConditionNode
{
    return (
        node &&
        node.type === "Condition" &&
        (
            node.name === "and" ||
            node.name === "or"
        )
    );
}

/**
 * Returns true if the given condition node is a logical condition with all operands being of type Component
 *
 * @param node
 */
export function isComposedComponentExpression(node: CNode): boolean
{
    return isLogicalCondition(node) && node.operands.every(o => o.type === "Component")
}


/**
 * Finds a component node with the given id.
 *
 * @param {Object} conditionNode    condition structure root
 * @param {String} id               component id
 *
 * @return {Object|null}    component node or `null`
 */
export function findComponentNode(conditionNode: CNode | null, id: string): CNode | null
{
    if (conditionNode == null)
    {
        return null;
    }
    if (conditionNode.type === "Component")
    {
        if (conditionNode.id === id)
        {
            return conditionNode;
        }
        return findComponentNode(conditionNode.condition, id);
    }

    if (isLogicalCondition(conditionNode))
    {
        const {operands} = conditionNode;

        if (operands)
        {
            for (let i = 0; i < operands.length; i++)
            {
                const operand = operands[i];
                const currentResult = findComponentNode(operand, id);
                if (currentResult != null)
                {
                    return currentResult;
                }
            }
        }
        return null;
    } else
    {
        return id === null ? component(null, conditionNode) : null;
    }


}


/**
 * Returns true if the two conditions are structurally equal: the same nodes in the same order, with equal names,
 * ids and values. Values compare by their JSON form, which is how they travel, so a Temporal value and the string the
 * server echoes for it are equal. `null` and `undefined` both mean "no condition" and equal each other.
 *
 * A condition that comes back unchanged from an update() is a different object with the same structure; this is how
 * to tell.
 *
 * @param a     condition or null
 * @param b     condition or null
 */
export function conditionsEqual(a: CNode | null | undefined, b: CNode | null | undefined): boolean
{
    if (a == null || b == null)
    {
        return a == null && b == null;
    }
    if (a === b)
    {
        return true;
    }
    if (a.type !== b.type)
    {
        return false;
    }

    switch (a.type)
    {
        case "Field":
            return a.name === (b as FieldNode).name;
        case "Condition":
        case "Operation":
        {
            const other = b as ConditionNode | OperationNode;
            return a.name === other.name &&
                a.operands.length === other.operands.length &&
                a.operands.every((operand, i) => conditionsEqual(operand, other.operands[i]));
        }
        case "Component":
        {
            const other = b as ComponentNode;
            return a.id === other.id && conditionsEqual(a.condition, other.condition);
        }
        case "Value":
        {
            const other = b as ValueNode;
            return a.scalarType === other.scalarType && rawEqual(a.value, other.value);
        }
        case "Values":
        {
            const other = b as ValuesNode;
            return a.scalarType === other.scalarType && rawEqual(a.values, other.values);
        }
        default:
            throw new Error("Invalid condition node: " + JSON.stringify(a));
    }
}


/**
 * The JSON form of a raw value: what JSON.stringify() would write for it, one level deep.
 */
function jsonForm(value: unknown): unknown
{
    if (value !== null && typeof value === "object" && typeof (value as { toJSON?: unknown }).toJSON === "function")
    {
        return (value as { toJSON: () => unknown }).toJSON();
    }
    return value;
}


/**
 * Deep equality of two raw values by their JSON form. Object keys holding `undefined` count as absent, as they do in
 * JSON.
 */
function rawEqual(a: unknown, b: unknown): boolean
{
    a = jsonForm(a);
    b = jsonForm(b);

    if (a === b)
    {
        return true;
    }
    if (a === null || b === null || typeof a !== "object" || typeof b !== "object")
    {
        return false;
    }

    if (Array.isArray(a) || Array.isArray(b))
    {
        return Array.isArray(a) && Array.isArray(b) &&
            a.length === b.length &&
            a.every((element, i) => rawEqual(element, b[i]));
    }

    const objA = a as Record<string, unknown>;
    const objB = b as Record<string, unknown>;
    const keysA = Object.keys(objA).filter(k => objA[k] !== undefined);
    const keysB = Object.keys(objB).filter(k => objB[k] !== undefined);

    return keysA.length === keysB.length && keysA.every(k => rawEqual(objA[k], objB[k]));
}


function isComponent(node: CNode | null): node is ComponentNode
{
    return node != null && node.type === "Component";
}


/**
 * A logical condition with the given operands, built fresh so the result has the fluent methods whatever the input
 * came as.
 */
function logical(name: string, operands: CNode[]): Condition
{
    const cond = new Condition(name);
    cond.operands = operands;
    return cond;
}


/**
 * Replaces the part of a condition that belongs to the holder of the given component id -- the write side of
 * ownedPart(), by the same rules.
 *
 * This is how several owners share one condition -- a grid's filter row, a search form, a picker -- each writing only
 * its own component and leaving the others as they are:
 *
 * - The component with the id gets the new term. A `null` term keeps the component as `component(id, null)`, so the
 *   owner finds its place again later; only the SQL transformation drops an empty component.
 * - A missing component is added as another operand of an `and` composition, or joined with `and()` to a lone
 *   component, so the result stays one flat level. Owners narrow what is shown, so adding means `and`.
 * - An `or` composition without the component is refused: joining it would widen the result, and wrapping the `or`
 *   would produce a second level. Put `component(id, null)` into the `or` where the owner's term belongs.
 * - No condition at all becomes the lone component, so the next owner to write joins it. A `null` term leaves it at
 *   no condition.
 * - Any other condition is owned whole and replaced by the plain term, without a marker.
 *
 * Returns the very same condition object when the owned part is structurally equal to the new term, so an unchanged
 * filter need not send an update().
 *
 * @param condition     condition or null
 * @param id            component id of the owner writing
 * @param term          new term of that owner, `null` for "filters nothing"
 *
 * @throws Error for an `or` composition that lacks the component
 */
export function updateComponent(condition: FilterExpression | null, id: string, term: FilterExpression | null): FilterExpression | null
{
    if (condition == null)
    {
        return term == null ? null : component(id, term);
    }

    if (isComponent(condition))
    {
        if (condition.id === id)
        {
            return conditionsEqual(condition.condition, term) ? condition : component(id, term);
        }
        return logical("and", [condition, component(id, term)]);
    }

    if (!isComposedComponentExpression(condition))
    {
        return conditionsEqual(condition, term) ? condition : term;
    }

    const {name, operands} = condition;
    const index = operands.findIndex(o => (o as ComponentNode).id === id);
    if (index < 0)
    {
        if (name !== "and")
        {
            throw new Error(
                "Cannot add component " + JSON.stringify(id) + " to an '" + name + "' composition: joining it " +
                "would widen the result. Put component(" + JSON.stringify(id) + ", null) into the composition."
            );
        }
        return logical(name, [...operands, component(id, term)]);
    }

    const existing = operands[index] as ComponentNode;
    if (conditionsEqual(existing.condition, term))
    {
        return condition;
    }

    const updated = operands.slice();
    updated[index] = component(id, term);
    return logical(name, updated);
}


/**
 * The part of a condition that belongs to the holder of the given component id:
 *
 * - in a composition of components, the term of its component, or `null` if there is none;
 * - in a lone component, its term if the component is its own, `null` if it belongs to someone else;
 * - any other condition is owned whole.
 *
 * Components exist on one level only: inside its component, an owner recognizes its terms by content, not by further
 * markers.
 *
 * @param condition     condition or null
 * @param id            component id of the owner reading
 *
 * @return the owned part, `null` for "nothing"
 */
export function ownedPart(condition: FilterExpression | null, id: string): FilterExpression | null
{
    if (condition == null)
    {
        return null;
    }

    if (isComponent(condition))
    {
        // a component wraps a condition, never a bare field or value
        return condition.id === id ? condition.condition as FilterExpression | null : null;
    }

    if (isComposedComponentExpression(condition))
    {
        const own = condition.operands.find(o => (o as ComponentNode).id === id) as ComponentNode | undefined;
        return own ? own.condition as FilterExpression | null : null;
    }

    return condition;
}


/**
 * Converts the given condition graph into simple js objects.
 *
 * The Filter DSL produces Filter nodes that are in fact instances of the Filter DSL types used to implement to
 * conditions/operations. This works fine in many cases, but sometimes it doesn't.
 *
 * One example is that mobx will ignore the instances and not create observables for them. Making the FilterDSL in general
 * observable would be possible, but would mean a huge overhead for a very exotic use-case.
 *
 * @param condition    Input condition, potentially consisting of DSL instances
 * @return condition as graph of objects / arrays
 */
export function toJSON(condition: CNode | null): RawValue
{
    if (!condition)
    {
        return null;
    }

    const {type} = condition;

    if (type === "Condition" || type === "Operation")
    {
        const {name, operands} = condition;

        return {
            type,
            name,
            operands: operands.map(toJSON)
        }
    } else if (type === "Component")
    {
        const {id, condition: wrapped} = condition;

        return {
            type,
            id,
            condition: toJSON(wrapped)
        }
    } else if (type === "Field")
    {
        const {name} = condition;

        return {
            type,
            name
        }
    } else if (type === "Value")
    {
        const {scalarType, value} = condition;

        return {
            type,
            scalarType,
            value
        }
    } else if (type === "Values")
    {
        const {scalarType, values} = condition;

        return {
            type,
            scalarType,
            values
        }
    } else
    {
        throw new Error("Invalid condition node: " + condition);
    }
}

export type ComputedValue = {
    name: string;
    args: RawValue[];
}

export function computedValue(name: string, args: RawValue[] = []): ValueNode
{
    return value({
            name,
            args
        },
        "ComputedValue"
    );
}


export function now(): ValueNode
{
    return computedValue("now")
}

export function today(): ValueNode
{
    return computedValue("today")
}

export function isComputedValue(raw: unknown): raw is ComputedValue
{
    // @ts-ignore
    return typeof raw.name === "string" && Array.isArray(raw.args)
}

/*
 * The DSL's method surface is derived from the same arity tables that
 * buildProto() uses at runtime, so the tables are the single source of truth.
 * Adding an operator is a one-line edit there and both halves follow.
 *
 * TypeScript cannot infer methods attached to a prototype in a loop - the
 * method set only exists after module evaluation - so the mapping from an
 * arity to a call signature has to be spelled out once, here.
 */
type CondFn<N extends number> =
    N extends 0 ? () => Condition :
    N extends 1 ? (a: CNode) => Condition :
    N extends 2 ? (a: CNode, b: CNode) => Condition :
    never;

type OpFn<N extends number> =
    N extends 0 ? () => Field :
    N extends 1 ? (a: CNode) => Field :
    N extends 2 ? (a: CNode, b: CNode) => Field :
    never;

type FieldConditions = {
    [K in keyof typeof FIELD_CONDITIONS]: CondFn<(typeof FIELD_CONDITIONS)[K]>
};

type FieldOperations = {
    [K in keyof typeof FIELD_OPERATIONS]: OpFn<(typeof FIELD_OPERATIONS)[K]>
};

export type Condition = ConditionNode & {
    [K in keyof typeof CONDITION_METHODS]: CondFn<(typeof CONDITION_METHODS)[K]>
};

export type Field = FieldNode & FieldConditions & FieldOperations; // Field prototype
export type Value = ValueNode & FieldConditions & FieldOperations; // Value prototype

