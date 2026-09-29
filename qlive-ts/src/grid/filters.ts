/*
 * Column filters: what turns a filter input's values into a condition term and a term back into values.
 *
 * A filter is a value an application imports and hands to a column. There is no registry of names.
 */
import type {ComponentType} from "react";
import {
    CNode,
    condition,
    conditionsEqual,
    Field,
    field,
    fieldConditionArity,
    FieldConditionName,
    FieldExpression,
    FilterExpression,
    RawValue,
    value
} from "../FilterDSL";
import {isViteDev} from "../util/viteEnv";

/**
 * Values of a filter's inputs as the inputs hold them: `null` where an input is empty.
 */
export type FilterInputValues<V extends unknown[] = unknown[]> = { [K in keyof V]: V[K] | null };

/**
 * What a filter input component gets.
 */
export interface ColumnFilterInputProps<V extends unknown[] = unknown[]>
{
    /** what the inputs filter, for their accessible names: the column's label */
    label: string;

    /** number of inputs */
    arity: number;

    /** current values, `null` for an empty input */
    values: FilterInputValues<V>;

    /** replaces the values */
    setValues(values: FilterInputValues<V>): void;

    /** the filter the input belongs to, for an input that takes what it offers from it */
    filter: ColumnFilter<V>;
}

/**
 * A column's filter.
 *
 * @typeParam V     values of the filter's inputs, one per input
 */
export interface ColumnFilter<V extends unknown[] = unknown[]>
{
    /**
     * Number of inputs.
     */
    arity: number;

    /**
     * Whether the filter takes effect with some of its inputs empty, like a date range open at one end. Then
     * `toCondition` is called once any input is filled, with `null` for the empty ones; otherwise only once every
     * input is.
     */
    partial?: boolean;

    /**
     * Whether the filter picks the related row of a relation column, and so filters its foreign key (`"ownerId"`)
     * rather than its first name field (`"owner.login"`). For a relation whose foreign key is one field.
     */
    key?: boolean;

    /**
     * Builds the term from the input values, `null` for "no filter". Only called once every input is filled, or with
     * `partial` once any is.
     *
     * @param target    what the column filters: a field node, or the expression of a computed column, either with
     *                  the builder methods, `target.between(from, to)`
     * @param values    one value per input
     */
    toCondition(target: Field, values: V): FilterExpression | null;

    /**
     * Recognizes a term as one this filter produced and returns the input values it came from, `null` for "not mine".
     * A filter without it can't take over a term set from outside the owner, which then shows as unclaimed. The
     * target is recognized by structure, `conditionsEqual(operand, target)`, which holds for a field and an
     * expression alike.
     *
     * @param target    what the column filters, as `toCondition` gets it
     * @param term      a term of the owner's part of the condition
     */
    fromCondition?(target: Field, term: FilterExpression): V | null;

    /**
     * Input component, where the default text inputs won't do.
     */
    Input?: ComponentType<ColumnFilterInputProps<V>>;
}

/**
 * A filter on one field or expression, as a filter row or a search form has one per input group.
 */
export interface FilterColumn
{
    /** what the column filters: a field path, or a FilterDSL expression like `field("numA").add(field("numB"))` */
    field: FieldExpression;

    /** the filter */
    filter: ColumnFilter<any>;

    /** what the inputs' accessible names call it, in place of the field path or the text form of the expression */
    label?: string;
}

/** the prototype of field and operation nodes, which holds the builder methods */
const BUILDERS = Object.getPrototypeOf(field(""));

/**
 * The node a filter compares, with the builder methods: a field path becomes a field node, an expression built with
 * the FilterDSL stays as it is, and a plain node like one parsed from JSON gets the methods on a copy.
 *
 * An expression has the methods of a field, as the DSL's builders give it, and its type says `Field` for that.
 *
 * @param expression    field path or expression
 */
export function filterTarget(expression: FieldExpression): Field
{
    if (typeof expression === "string")
    {
        return field(expression);
    }
    return (typeof (expression as Partial<Field>).eq === "function"
        ? expression
        : Object.assign(Object.create(BUILDERS), expression)) as Field;
}

/**
 * The label of a filter column: the one it states, or else its field path, or a text form of its expression,
 * `add(numA, numB)`.
 *
 * @param column    the filter column
 */
export function filterLabel(column: FilterColumn): string
{
    return column.label ?? expressionText(column.field);
}

function expressionText(expression: FieldExpression): string
{
    if (typeof expression === "string")
    {
        return expression;
    }
    switch (expression.type)
    {
        case "Field":
            return expression.name;
        case "Value":
            return JSON.stringify(expression.value);
        case "Values":
            return JSON.stringify(expression.values);
        case "Condition":
        case "Operation":
            return expression.name + "(" + expression.operands.map(expressionText).join(", ") + ")";
        case "Component":
            return expression.condition ? expressionText(expression.condition) : "";
    }
}

const INTEGER_SCALARS = new Set(["Int", "Short", "Byte"]);
const NUMBER_SCALARS = new Set(["Float"]);

/**
 * Converts an input's text into a value of the scalar type, `undefined` where the text isn't one.
 */
function parseValue(text: string, scalarType: string): RawValue | undefined
{
    if (INTEGER_SCALARS.has(scalarType) || NUMBER_SCALARS.has(scalarType))
    {
        const trimmed = text.trim();
        const number = Number(trimmed);
        if (trimmed === "" || isNaN(number) || (INTEGER_SCALARS.has(scalarType) && !Number.isInteger(number)))
        {
            return undefined;
        }
        return number;
    }
    if (scalarType === "Boolean")
    {
        return text === "true" ? true : text === "false" ? false : undefined;
    }
    return text;
}

/**
 * A filter applying one field condition: `operatorFilter("containsIgnoreCase")` filters the column's field for the
 * text typed, `operatorFilter("between", "Int")` takes two numbers. It has one input per operand besides the field,
 * and it reads back exactly the terms it writes: that condition on that field, with values as operands. On a
 * computed column it applies the condition to the column's expression instead, `between` on a sum.
 *
 * The inputs are text. The filter converts it to the scalar type: a number for Int, Short, Byte and Float, a boolean
 * for "true" and "false" with Boolean, the text as is for everything else. Text that isn't a value of the type
 * filters nothing, so a half-typed number doesn't send a query.
 *
 * @param name          field condition, with at least one operand
 * @param scalarType    scalar type of the field. Default "String".
 */
export function operatorFilter(name: FieldConditionName, scalarType: string = "String"): ColumnFilter<string[]>
{
    const arity = fieldConditionArity(name);
    if (!arity)
    {
        throw new Error(
            "operatorFilter(" + JSON.stringify(name) + ") needs a field condition taking values; " +
            (arity === 0 ? "this one takes none." : "there is no such condition.")
        );
    }
    if (name === "in")
    {
        throw new Error("operatorFilter(\"in\") has no text form for a list of values. Write a filter function for it.");
    }

    return {
        arity,

        toCondition(target: Field, values: string[]): FilterExpression | null
        {
            const operands: CNode[] = [];
            for (const text of values)
            {
                const parsed = parseValue(text, scalarType);
                if (parsed === undefined)
                {
                    return null;
                }
                operands.push(value(parsed, scalarType));
            }
            return condition(name, [target, ...operands]) as FilterExpression;
        },

        fromCondition(target: Field, term: FilterExpression): string[] | null
        {
            if (term.type !== "Condition" || term.name !== name || term.operands.length !== arity + 1)
            {
                return null;
            }
            const [filtered, ...operands] = term.operands;
            if (!conditionsEqual(filtered, target) || !operands.every(o => o.type === "Value"))
            {
                return null;
            }
            return operands.map(o => o.type === "Value" && o.value !== null ? String(o.value) : "");
        }
    };
}

/**
 * True if every input of a filter has a value. An empty string counts as no value.
 */
export function filled(values: readonly unknown[]): boolean
{
    return values.every(v => v !== null && v !== undefined && v !== "");
}

/**
 * True if a filter's inputs hold enough for it to take effect: all of them, or with `partial` any of them.
 *
 * @param filter    the filter
 * @param values    its input values
 */
export function ready(filter: ColumnFilter<any>, values: readonly unknown[]): boolean
{
    return filter.partial ? values.some(v => filled([v])) : filled(values);
}

/**
 * How the terms of an owner's part of a condition are distributed over its filter columns.
 */
export interface ClaimedTerms
{
    /** per column, the term it claimed, or `null` */
    terms: (FilterExpression | null)[];

    /** per column, the input values its term came from, or `null`, also for a term the column wrote itself */
    values: (unknown[] | null)[];

    /** terms no column claimed, or more than one did */
    unclaimed: FilterExpression[];
}

/**
 * Hands the terms of an owner's part of a condition to the columns that produced them, by asking each column's
 * filter to recognize them (`fromCondition`).
 *
 * The part is one term per active column, combined with `and()`, which unwraps a single operand. So a column term
 * that is itself an `and` looks like several terms when it is the only one active, and the part is read in two
 * steps: the whole part is offered to every column first, and if exactly one claims it, that column is the only one
 * active. Otherwise the operands of an `and` are offered one by one.
 *
 * A term no column claims is unclaimed, and so is a term two columns claim, which is a configuration error warned
 * about in development: two columns filtering one field with one operator. A column claims one term at most; a
 * second one it recognizes is unclaimed.
 *
 * A term a column wrote itself is that column's without asking its filter, which is how a filter without
 * `fromCondition` keeps its own terms. Its values are `null` then: the owner still has what it wrote them from.
 *
 * @param part      the owner's part of the condition, as from `ownedPart()`
 * @param columns   the owner's filter columns
 * @param written   per column, terms it wrote itself
 */
export function claimTerms(
    part: FilterExpression | null,
    columns: readonly FilterColumn[],
    written: readonly (readonly (FilterExpression | null)[])[] = []
): ClaimedTerms
{
    const result: ClaimedTerms = {
        terms: columns.map(() => null),
        values: columns.map(() => null),
        unclaimed: []
    };

    if (part == null)
    {
        return result;
    }

    const claims = (term: FilterExpression) => {
        const own = columns.findIndex((_, index) => written[index]?.some(t => conditionsEqual(t, term)));
        if (own >= 0)
        {
            return [{index: own, values: null}];
        }
        const found: { index: number, values: unknown[] | null }[] = [];
        columns.forEach(({field, filter}, index) => {
            const values = filter.fromCondition?.(filterTarget(field), term);
            if (values)
            {
                found.push({index, values});
            }
        });
        if (found.length > 1 && isViteDev())
        {
            console.warn(
                "Filter columns " + found.map(f => JSON.stringify(filterLabel(columns[f.index]))).join(" and ") +
                " all recognize the same term, so none of them gets it:", term
            );
        }
        return found;
    };

    const assign = (term: FilterExpression, found: { index: number, values: unknown[] | null }[]) => {
        const claim = found.length === 1 ? found[0] : null;
        if (claim && result.terms[claim.index] === null)
        {
            result.terms[claim.index] = term;
            result.values[claim.index] = claim.values;
        }
        else
        {
            result.unclaimed.push(term);
        }
    };

    const whole = claims(part);
    if (whole.length === 1 || part.type !== "Condition" || part.name !== "and")
    {
        assign(part, whole);
    }
    else
    {
        for (const operand of part.operands)
        {
            const term = operand as FilterExpression;
            assign(term, claims(term));
        }
    }
    return result;
}

