import {getConverter} from "./converter";
import {CNode, ConditionNode, FieldExpression, FilterExpression, isComputedValue, OperationNode, RawValue} from "./FilterDSL";
import {QueryConfig} from "./QueryDocument";
import {Temporal} from "./temporal";
import {isListType, objectFields, unwrapAll} from "./type-utils";
import {compareNumbers, isNumericType, scalarCompare, scalarEqual} from "./util/scalar";

/*
 * The FilterDSL evaluated over JavaScript objects: whether a row matches a condition, and the order of rows under a
 * list of sort fields.
 *
 * It stands in for the database -- a created row shows under a query's condition when the query would return it once
 * saved -- so it follows the SQL path rather than the server's payload evaluator where the two differ:
 *
 * - The logic is three-valued. A comparison with null is unknown, not false, and stays unknown under `not`; a row
 *   matches only where the whole condition is true.
 * - A path through a to-many relation asks whether some element satisfies the comparison, each comparison on its
 *   own, as the SQL path's EXISTS does. Two paths through the same relation in one comparison read the same element.
 * - A value is read as the type of what it is compared to, the way the SQL path binds it as the field's type.
 * - Arithmetic has the type of its first operand: dividing an Int truncates.
 *
 * What the browser can't match exactly: text orders by the user's locale rather than the database's collation (see
 * scalarCompare()), `likeRegex` is a JavaScript regular expression rather than a POSIX one, `toString` of a
 * Timestamp is its ISO form rather than the database's, and `now()` and `today()` are the browser's clock.
 */

/** true, false, or unknown: a comparison involving null */
type Truth = boolean | null;

/**
 * Where the fields of one comparison are read: the row under "", and the element each to-many relation on the way
 * stands at, under the path of the relation.
 */
type Scope = Map<string, unknown>;

/** a to-many relation a comparison reaches through */
interface ListScope
{
    /** path of the relation from the row */
    path: string;
    /** the scope the list is read from: "" for the row, or the relation the list is below */
    parent: string;
    /** field names from the parent to the list */
    segments: string[];
}

/** a compiled operand: what type it is and how to read it */
interface Operand
{
    type: string;

    read(scope: Scope): unknown;
}

/** what a comparison or operation compiles in: the row type, and the to-many relations its fields went through */
interface Context
{
    rowType: string;
    scopes: Map<string, ListScope>;
}

type Test = (row: object) => Truth;

const LOGIC = new Set(["and", "or", "not", "andNot", "orNot"]);

/**
 * What the evaluator refuses, and why. Sorting belongs in the sort fields.
 */
const NOT_CONDITIONS: Record<string, string> = {
    asc: "sorts, it doesn't filter",
    desc: "sorts, it doesn't filter"
};

// ---------------------------------------------------------------------------------------------------------------------
// values
// ---------------------------------------------------------------------------------------------------------------------

function isNull(value: unknown): boolean
{
    return value === null || value === undefined;
}

/**
 * The value as JSON has it, which is how the server receives a condition's values.
 */
function wireOf(raw: unknown): unknown
{
    const json = JSON.stringify(raw);
    return json === undefined ? null : JSON.parse(json);
}

/**
 * A wire value in the live form of the type, as a query result would hold it. A number or Boolean read as text is
 * its text, and text read as a Boolean is `true` or `false`, the way the database casts a value bound as the type.
 */
function liveOf(wire: unknown, type: string): unknown
{
    if (isNull(wire))
    {
        return null;
    }
    const converter = getConverter(type);
    if (converter)
    {
        return converter.fromServer(wire, type);
    }
    if ((type === "String" || type === "ID") && (typeof wire === "number" || typeof wire === "boolean"))
    {
        return String(wire);
    }
    if (type === "Boolean" && typeof wire === "string")
    {
        const lower = wire.trim().toLowerCase();
        if (lower === "true" || lower === "false")
        {
            return lower === "true";
        }
        throw new Error("Not a Boolean: " + JSON.stringify(wire));
    }
    return wire;
}

/**
 * A value as text, the way `toString` and the text conditions take it: a string as it is, anything else in its wire
 * form.
 */
function text(value: unknown): string
{
    if (typeof value === "string")
    {
        return value;
    }
    if (typeof value === "bigint")
    {
        return value.toString();
    }
    const wire = wireOf(value);
    return typeof wire === "string" ? wire : JSON.stringify(wire);
}

function numberLike(value: unknown): value is number | bigint | string
{
    return typeof value === "number" || typeof value === "bigint" || typeof value === "string";
}

/**
 * Whether two values of the type are the same value. Numbers are equal by value across their forms.
 */
function equal(type: string, a: unknown, b: unknown): boolean
{
    if (isNumericType(type) && numberLike(a) && numberLike(b) ||
        (typeof a === "number" || typeof a === "bigint") && (typeof b === "number" || typeof b === "bigint"))
    {
        return compareNumbers(a as number | bigint | string, b as number | bigint | string) === 0;
    }
    return scalarEqual(a, b);
}

// ---------------------------------------------------------------------------------------------------------------------
// conditions
// ---------------------------------------------------------------------------------------------------------------------

type ConditionImpl = (operands: unknown[], type: string) => Truth;

/**
 * A condition that is unknown where any of its operands is null.
 */
function defined(arity: number, impl: (operands: unknown[], type: string) => boolean): { arity: number, impl: ConditionImpl }
{
    return {arity, impl: (operands, type) => operands.some(isNull) ? null : impl(operands, type)};
}

/** the regular expressions compiled so far, by source; cleared when it gets long, as typing a pattern makes many */
const regexes = new Map<string, RegExp>();

/**
 * Whether the pattern occurs in the text, anywhere in it, as PostgreSQL's `~` has it.
 */
function find(value: unknown, pattern: unknown): boolean
{
    const source = text(pattern);
    let regex = regexes.get(source);
    if (!regex)
    {
        regex = new RegExp(source);
        if (regexes.size >= 100)
        {
            regexes.clear();
        }
        regexes.set(source, regex);
    }
    return regex.test(text(value));
}

function between(type: string, value: unknown, low: unknown, high: unknown): boolean
{
    return scalarCompare(type, value, low) >= 0 && scalarCompare(type, value, high) <= 0;
}

function betweenSymmetric(type: string, value: unknown, a: unknown, b: unknown): boolean
{
    return scalarCompare(type, a, b) <= 0 ? between(type, value, a, b) : between(type, value, b, a);
}

function distinct(type: string, a: unknown, b: unknown): boolean
{
    return isNull(a) || isNull(b) ? isNull(a) !== isNull(b) : !equal(type, a, b);
}

/**
 * The comparisons, by name, with the number of operands each takes, the one it applies to included.
 */
const CONDITIONS: Record<string, { arity: number, impl: ConditionImpl }> = {
    eq: defined(2, ([a, b], t) => equal(t, a, b)),
    equal: defined(2, ([a, b], t) => equal(t, a, b)),
    ne: defined(2, ([a, b], t) => !equal(t, a, b)),
    notEqual: defined(2, ([a, b], t) => !equal(t, a, b)),

    lt: defined(2, ([a, b], t) => scalarCompare(t, a, b) < 0),
    lessThan: defined(2, ([a, b], t) => scalarCompare(t, a, b) < 0),
    le: defined(2, ([a, b], t) => scalarCompare(t, a, b) <= 0),
    lessOrEqual: defined(2, ([a, b], t) => scalarCompare(t, a, b) <= 0),
    gt: defined(2, ([a, b], t) => scalarCompare(t, a, b) > 0),
    greaterThan: defined(2, ([a, b], t) => scalarCompare(t, a, b) > 0),
    ge: defined(2, ([a, b], t) => scalarCompare(t, a, b) >= 0),
    greaterOrEqual: defined(2, ([a, b], t) => scalarCompare(t, a, b) >= 0),

    between: defined(3, ([v, low, high], t) => between(t, v, low, high)),
    notBetween: defined(3, ([v, low, high], t) => !between(t, v, low, high)),
    betweenSymmetric: defined(3, ([v, a, b], t) => betweenSymmetric(t, v, a, b)),
    notBetweenSymmetric: defined(3, ([v, a, b], t) => !betweenSymmetric(t, v, a, b)),

    isDistinctFrom: {arity: 2, impl: ([a, b], t) => distinct(t, a, b)},
    isNotDistinctFrom: {arity: 2, impl: ([a, b], t) => !distinct(t, a, b)},

    equalIgnoreCase: defined(2, ([a, b]) => text(a).toLowerCase() === text(b).toLowerCase()),
    notEqualIgnoreCase: defined(2, ([a, b]) => text(a).toLowerCase() !== text(b).toLowerCase()),
    contains: defined(2, ([a, b]) => text(a).includes(text(b))),
    notContains: defined(2, ([a, b]) => !text(a).includes(text(b))),
    containsIgnoreCase: defined(2, ([a, b]) => text(a).toLowerCase().includes(text(b).toLowerCase())),
    notContainsIgnoreCase: defined(2, ([a, b]) => !text(a).toLowerCase().includes(text(b).toLowerCase())),
    startsWith: defined(2, ([a, b]) => text(a).startsWith(text(b))),
    endsWith: defined(2, ([a, b]) => text(a).endsWith(text(b))),
    likeRegex: defined(2, ([a, b]) => find(a, b)),
    notLikeRegex: defined(2, ([a, b]) => !find(a, b)),

    isNull: {arity: 1, impl: ([v]) => isNull(v)},
    isNotNull: {arity: 1, impl: ([v]) => !isNull(v)},
    isTrue: defined(1, ([v]) => v === true),
    isFalse: defined(1, ([v]) => v === false)
};

// ---------------------------------------------------------------------------------------------------------------------
// operations
// ---------------------------------------------------------------------------------------------------------------------

/** the integer types, whose arithmetic is exact and whose division truncates */
const INTEGER_TYPES = new Set(["Int", "Short", "Byte", "Long", "BigInteger"]);

/** the integer types small enough to be JavaScript numbers */
const SMALL_INTEGER_TYPES = new Set(["Int", "Short", "Byte"]);

function integer(value: unknown): bigint
{
    if (typeof value === "bigint")
    {
        return value;
    }
    if (typeof value === "number" && !Number.isInteger(value))
    {
        throw new Error("Not a whole number: " + value);
    }
    try
    {
        return BigInt(typeof value === "string" ? value.trim() : value as number);
    }
    catch (e)
    {
        throw new Error("Not a whole number: " + JSON.stringify(value));
    }
}

function number(value: unknown): number
{
    const result = typeof value === "number" ? value : Number(typeof value === "bigint" ? value : text(value));
    if (isNaN(result))
    {
        throw new Error("Not a number: " + JSON.stringify(text(value)));
    }
    return result;
}

/** an integer result in the form the type's values take */
function integral(type: string, value: bigint): number | bigint
{
    return SMALL_INTEGER_TYPES.has(type) ? Number(value) : value;
}

function nonZero<T extends number | bigint>(divisor: T): T
{
    if (Number(divisor) === 0)
    {
        throw new Error("Division by zero in a filter condition");
    }
    return divisor;
}

/** a value operation */
interface OperationImpl
{
    /** number of operands, the one it applies to included */
    arity: number;

    /** type of the result, from the type of the operand it applies to */
    result(type: string): string;

    /** the result, from operands none of which is null */
    impl(operands: unknown[], type: string): unknown;
}

/**
 * An arithmetic operation, computed as the type of its first operand: exactly for the integer types, as JavaScript
 * numbers otherwise.
 */
function arithmetic(
    arity: number,
    onIntegers: (operands: bigint[]) => bigint,
    onNumbers: (operands: number[]) => number
): OperationImpl
{
    return {
        arity,
        result: type => type,
        impl: (operands, type) => INTEGER_TYPES.has(type)
            ? integral(type, onIntegers(operands.map(integer)))
            : onNumbers(operands.map(number))
    };
}

/**
 * A bit operation, on the whole numbers the operands are, decimal strings included: the value these exist for is a
 * field mask wider than a JSON number holds.
 */
function bits(arity: number, impl: (operands: bigint[]) => bigint): OperationImpl
{
    return {
        arity,
        result: type => type,
        impl: (operands, type) => integral(type, impl(operands.map(integer)))
    };
}

/** a text operation, whose result is a String */
function textual(arity: number, impl: (operands: string[]) => string): OperationImpl
{
    return {
        arity,
        result: () => "String",
        impl: operands => impl(operands.map(text))
    };
}

const add = arithmetic(2, ([a, b]) => a + b, ([a, b]) => a + b);
const subtract = arithmetic(2, ([a, b]) => a - b, ([a, b]) => a - b);
const multiply = arithmetic(2, ([a, b]) => a * b, ([a, b]) => a * b);
const divide = arithmetic(2, ([a, b]) => a / nonZero(b), ([a, b]) => a / nonZero(b));
const modulo = arithmetic(2, ([a, b]) => a % nonZero(b), ([a, b]) => a % nonZero(b));
const power = arithmetic(2, ([a, b]) => a ** b, ([a, b]) => a ** b);
const negate = arithmetic(1, ([a]) => -a, ([a]) => -a);

/**
 * The value operations, by name, with the number of operands each takes and the type of their result.
 */
const OPERATIONS: Record<string, OperationImpl> = {
    add, plus: add,
    subtract, sub: subtract, minus: subtract,
    mul: multiply, times: multiply, multiply,
    div: divide, divide,
    mod: modulo, modulo, rem: modulo,
    pow: power, power,
    neg: negate, unaryMinus: negate,
    unaryPlus: arithmetic(1, ([a]) => a, ([a]) => a),

    bitAnd: bits(2, ([a, b]) => a & b),
    bitOr: bits(2, ([a, b]) => a | b),
    bitXor: bits(2, ([a, b]) => a ^ b),
    bitNand: bits(2, ([a, b]) => ~(a & b)),
    bitNor: bits(2, ([a, b]) => ~(a | b)),
    bitXNor: bits(2, ([a, b]) => ~(a ^ b)),
    bitNot: bits(1, ([a]) => ~a),
    shl: bits(2, ([a, b]) => a << b),
    shr: bits(2, ([a, b]) => a >> b),

    lower: textual(1, ([a]) => a.toLowerCase()),
    upper: textual(1, ([a]) => a.toUpperCase()),
    concat: textual(2, ([a, b]) => a + b),
    toString: textual(1, ([a]) => a)
};

// ---------------------------------------------------------------------------------------------------------------------
// compiling
// ---------------------------------------------------------------------------------------------------------------------

function conditionError(rowType: string, message: string): Error
{
    return new Error("Condition on " + rowType + ": " + message);
}

function checkArity(ctx: Context, name: string, expected: number, actual: number)
{
    if (expected !== actual)
    {
        throw conditionError(ctx.rowType, "'" + name + "' takes " + expected + " operand(s), not " + actual + ".");
    }
}

/**
 * Reads a path below an object. Null where anything on the way is; a key the object doesn't have is an error, since
 * the query doesn't select it and a condition on it would never match.
 */
function readPath(start: unknown, segments: readonly string[], path: string, rowType: string): unknown
{
    let current = start;
    for (const name of segments)
    {
        if (isNull(current))
        {
            return null;
        }
        if (!(name in (current as object)))
        {
            throw conditionError(rowType, "the rows have no " + JSON.stringify(path) + ". Select it in the query.");
        }
        current = (current as any)[name];
    }
    return isNull(current) ? null : current;
}

/**
 * A field path, checked against the schema: every step a field of the type it is on, a to-many relation noted as a
 * scope, the last step a scalar.
 */
function compileField(ctx: Context, path: string): Operand
{
    const segments = path.split(".");
    let owner = ctx.rowType;
    // the scope the field is read from and the segments below it
    let base = "";
    let start = 0;
    for (let i = 0; i < segments.length; i++)
    {
        const name = segments[i];
        const field = objectFields(owner).find(f => f.name === name);
        if (!field)
        {
            throw conditionError(ctx.rowType, owner + " has no field " + JSON.stringify(name) + ".");
        }
        const named = unwrapAll(field.type);
        const last = i === segments.length - 1;
        if (named.kind !== "OBJECT")
        {
            if (!last)
            {
                throw conditionError(ctx.rowType, name + " of " + owner + " is a " + named.name + ", with no fields below it.");
            }
            if (isListType(field.type))
            {
                throw conditionError(ctx.rowType, JSON.stringify(path) + " is a list of values, and a condition compares one.");
            }
            const rest = segments.slice(start);
            const from = base;
            return {
                type: named.name!,
                read: scope => readPath(scope.get(from), rest, path, ctx.rowType)
            };
        }
        if (last)
        {
            throw conditionError(ctx.rowType, JSON.stringify(path) + " is a " + named.name + ", not a value. Name a field of it.");
        }
        if (isListType(field.type))
        {
            const scopePath = segments.slice(0, i + 1).join(".");
            if (!ctx.scopes.has(scopePath))
            {
                ctx.scopes.set(scopePath, {path: scopePath, parent: base, segments: segments.slice(start, i + 1)});
            }
            base = scopePath;
            start = i + 1;
        }
        owner = named.name!;
    }
    throw conditionError(ctx.rowType, "empty field path.");
}

/**
 * A value node. Its value is read as the type it is compared to, where there is one, the way the SQL path binds it.
 */
function compileValue(raw: RawValue, scalarType: string, hint: string | null): Operand
{
    if (scalarType === "ComputedValue" && isComputedValue(raw))
    {
        const computed = raw.name === "now"
            ? {wire: Temporal.Now.instant().toString(), type: "Timestamp"}
            : raw.name === "today"
                ? {wire: Temporal.Now.plainDateISO().toString(), type: "Date"}
                : null;
        if (!computed)
        {
            throw new Error("Unknown computed filter value: " + raw.name);
        }
        const type = hint ?? computed.type;
        const value = liveOf(computed.wire, type);
        return {type, read: () => value};
    }
    const type = hint ?? scalarType;
    const value = liveOf(wireOf(raw), type);
    return {type, read: () => value};
}

/**
 * An operand: a field, a value, or an operation on operands.
 */
function compileOperand(ctx: Context, node: CNode, hint: string | null): Operand
{
    switch (node?.type)
    {
        case "Field":
            return compileField(ctx, node.name);
        case "Value":
            return compileValue(node.value, node.scalarType, hint);
        case "Operation":
            return compileOperation(ctx, node);
        case "Values":
            throw conditionError(ctx.rowType, "a list of values is only valid as the operand of 'in'.");
        default:
            throw conditionError(ctx.rowType, "cannot use " + JSON.stringify(node) + " as a value.");
    }
}

function compileOperation(ctx: Context, node: OperationNode): Operand
{
    const op = OPERATIONS[node.name];
    if (!op)
    {
        throw conditionError(
            ctx.rowType,
            CONDITIONS[node.name] ? "'" + node.name + "' produces a condition, not a value." : "invalid filter operator: " + node.name
        );
    }
    checkArity(ctx, node.name, op.arity, node.operands.length);
    const [receiver, ...rest] = node.operands;
    const first = compileOperand(ctx, receiver, null);
    const operands = [first, ...rest.map(operand => compileOperand(ctx, operand, first.type))];
    const type = op.result(first.type);
    return {
        type,
        read: scope => {
            const values = operands.map(operand => operand.read(scope));
            return values.some(isNull) ? null : op.impl(values, first.type);
        }
    };
}

/**
 * A comparison, true where some element of every to-many relation it reaches through satisfies it.
 */
function compileComparison(rowType: string, node: ConditionNode): Test
{
    const ctx: Context = {rowType, scopes: new Map()};
    const test = compileTest(ctx, node);

    const scopes = [...ctx.scopes.values()].sort((a, b) => a.path.split(".").length - b.path.split(".").length);
    if (!scopes.length)
    {
        return row => test(new Map([["", row]]));
    }

    const some = (index: number, scope: Scope): boolean => {
        if (index === scopes.length)
        {
            return test(scope) === true;
        }
        const {path, parent, segments} = scopes[index];
        const list = readPath(scope.get(parent), segments, path, rowType);
        for (const element of Array.isArray(list) ? list : [])
        {
            scope.set(path, element);
            if (some(index + 1, scope))
            {
                return true;
            }
        }
        scope.delete(path);
        return false;
    };
    return row => some(0, new Map([["", row]]));
}

function compileTest(ctx: Context, node: ConditionNode): (scope: Scope) => Truth
{
    const {name, operands} = node;
    if (!operands?.length)
    {
        throw conditionError(ctx.rowType, "'" + name + "' has no operands.");
    }

    if (name === "in")
    {
        const [target, list] = operands;
        if (operands.length !== 2 || list.type !== "Values")
        {
            throw conditionError(ctx.rowType, "'in' takes exactly one list of values.");
        }
        const receiver = compileOperand(ctx, target, null);
        const candidates = (list.values ?? []).map(raw => liveOf(wireOf(raw), receiver.type));
        return scope => {
            const value = receiver.read(scope);
            if (isNull(value))
            {
                return null;
            }
            return candidates.some(candidate => !isNull(candidate) && equal(receiver.type, value, candidate))
                ? true
                : candidates.some(isNull) ? null : false;
        };
    }

    const op = CONDITIONS[name];
    if (!op)
    {
        const refused = NOT_CONDITIONS[name];
        throw conditionError(
            ctx.rowType,
            refused ? "'" + name + "' " + refused + "."
                : OPERATIONS[name] ? "'" + name + "' produces a value, not a condition."
                    : "invalid filter operator: " + name
        );
    }
    checkArity(ctx, name, op.arity, operands.length);
    const [target, ...rest] = operands;
    const receiver = compileOperand(ctx, target, null);
    const compiled = [receiver, ...rest.map(operand => compileOperand(ctx, operand, receiver.type))];
    return scope => op.impl(compiled.map(operand => operand.read(scope)), receiver.type);
}

function not(truth: Truth): Truth
{
    return truth === null ? null : !truth;
}

function and(a: Truth, b: Truth): Truth
{
    return a === false || b === false ? false : a === null || b === null ? null : true;
}

function or(a: Truth, b: Truth): Truth
{
    return a === true || b === true ? true : a === null || b === null ? null : false;
}

/**
 * A condition, `null` for one that constrains nothing: an empty component, or a logic operator whose operands all
 * are.
 */
function compileCondition(rowType: string, node: CNode | null): Test | null
{
    if (node === null || node === undefined)
    {
        return null;
    }
    if (node.type === "Component")
    {
        return compileCondition(rowType, node.condition);
    }
    if (node.type !== "Condition")
    {
        throw conditionError(rowType, "cannot use a " + node.type + " node as a condition.");
    }
    if (!LOGIC.has(node.name))
    {
        return compileComparison(rowType, node);
    }

    const operands = (node.operands ?? [])
        .map(operand => compileCondition(rowType, operand))
        .filter((test): test is Test => test !== null);
    if (!operands.length)
    {
        return null;
    }
    if (node.name === "not")
    {
        if (operands.length !== 1)
        {
            throw conditionError(rowType, "'not' takes exactly one operand.");
        }
        const [operand] = operands;
        return row => not(operand(row));
    }
    const [first, ...rest] = operands;
    const combine = node.name === "and" ? and
        : node.name === "or" ? or
            : node.name === "andNot" ? (a: Truth, b: Truth) => and(a, not(b))
                : (a: Truth, b: Truth) => or(a, not(b));
    return row => rest.reduce((truth, operand) => combine(truth, operand(row)), first(row));
}

// ---------------------------------------------------------------------------------------------------------------------
// public
// ---------------------------------------------------------------------------------------------------------------------

/**
 * Compiles a condition into a test of rows: whether the server would return a row for a query with that condition.
 *
 *     const matches = conditionPredicate("Foo", doc.config.condition);
 *     const shown = created.filter(matches);
 *
 * Everything the condition decides by itself is checked here, once: a field the type doesn't have, an operator that
 * doesn't exist or gets the wrong number of operands, a value its type can't read. A row missing a field the
 * condition reads is an error when it is tested -- the query doesn't select that field.
 *
 * It follows the SQL path: three-valued logic, so a row matches only where the condition is true and a comparison
 * with null never is; a path through a to-many relation asks whether some element matches; values read as the type
 * of what they are compared to. Text orders by the user's locale rather than the database's collation, `likeRegex`
 * is a JavaScript regular expression, and `now()` and `today()` are the browser's clock.
 *
 * @param type          GraphQL type name of the rows
 * @param condition     FilterDSL condition, `null` for "all rows"
 *
 * @returns test of a row, in the live form a query result or a working set draft has
 */
export function conditionPredicate<R extends object = any>(type: string, condition: FilterExpression | null): (row: R) => boolean
{
    const test = compileCondition(type, condition);
    return test ? row => test(row) === true : () => true;
}

/**
 * The key and direction of one sort field.
 */
function compileSortField(type: string, sortField: FieldExpression): { key: Operand, descending: boolean }
{
    const ctx: Context = {rowType: type, scopes: new Map()};
    let key: Operand;
    let descending = false;
    if (typeof sortField === "string")
    {
        descending = sortField.startsWith("!");
        key = compileField(ctx, descending ? sortField.substring(1) : sortField);
    }
    else if (sortField.type === "Operation" && (sortField.name === "asc" || sortField.name === "desc"))
    {
        checkArity(ctx, sortField.name, 1, sortField.operands.length);
        descending = sortField.name === "desc";
        key = compileOperand(ctx, sortField.operands[0], null);
    }
    else
    {
        key = compileOperand(ctx, sortField, null);
    }
    if (ctx.scopes.size)
    {
        throw conditionError(type, "sort fields cannot follow a to-many relation: a set of rows has no one value to sort by.");
    }
    return {key, descending};
}

/**
 * Compiles sort fields into a comparison of rows, for `Array.prototype.sort()`.
 *
 *     rows.sort(sortComparator("Foo", ["!num", "name"]))
 *
 * Sort fields come in every form a query config takes them: a field path, `"!"` in front for descending, a field or
 * expression node, or one in `asc()` or `desc()`. Nulls come last ascending and first descending, as in PostgreSQL.
 * Values order by scalarCompare(). Rows equal under every sort field keep their order, sort() being stable.
 *
 * @param type          GraphQL type name of the rows
 * @param sortFields    sort fields, most significant first
 *
 * @returns comparison of two rows
 */
export function sortComparator<R extends object = any>(type: string, sortFields: readonly FieldExpression[]): (a: R, b: R) => number
{
    const keys = sortFields.map(sortField => compileSortField(type, sortField));
    return (a, b) => {
        const scopeA: Scope = new Map([["", a]]);
        const scopeB: Scope = new Map([["", b]]);
        for (const {key, descending} of keys)
        {
            const valueA = key.read(scopeA);
            const valueB = key.read(scopeB);
            const order = valueA === null
                ? (valueB === null ? 0 : 1)
                : valueB === null ? -1 : Math.sign(scalarCompare(key.type, valueA, valueB));
            if (order !== 0)
            {
                return descending ? -order : order;
            }
        }
        return 0;
    };
}

/**
 * What a query with the config returns from the rows: the page of the matching rows in order, and how many match.
 *
 * @internal
 */
export function evaluateQuery<R extends object>(type: string, rows: readonly R[], config: QueryConfig): { rows: R[], rowCount: number }
{
    const matching = rows.filter(conditionPredicate<R>(type, config.condition));
    if (config.sortFields.length)
    {
        matching.sort(sortComparator<R>(type, config.sortFields));
    }
    const {offset, pageSize} = config;
    return {
        rows: pageSize > 0 ? matching.slice(offset, offset + pageSize) : matching.slice(offset),
        rowCount: matching.length
    };
}
