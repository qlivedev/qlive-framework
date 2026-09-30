import type {JSX} from "react";

import {and, CNode, condition, conditionsEqual, FilterExpression, value} from "../FilterDSL";
import i18n from "../i18n";
import {Temporal} from "../temporal";
import {ColumnFilter, ColumnFilterInputProps} from "./filters";

/**
 * A date as a date input holds it, `"2026-09-28"`, or `null` where the text isn't one.
 */
function parseDate(text: string | null): Temporal.PlainDate | null
{
    if (!text)
    {
        return null;
    }
    try
    {
        return Temporal.PlainDate.from(text.trim(), {overflow: "reject"});
    }
    catch (e)
    {
        return null;
    }
}

/**
 * The value of a condition operand, if it is a value of the scalar type.
 */
function operandValue(node: unknown, scalarType: string): string | null
{
    const operand = node as { type?: string, scalarType?: string, value?: unknown };
    return operand?.type === "Value" && operand.scalarType === scalarType && typeof operand.value === "string"
        ? operand.value
        : null;
}

/**
 * The operands of a condition on the target besides the target, `null` if the term is something else.
 */
function fieldCondition(term: CNode, name: string, target: CNode): unknown[] | null
{
    if (term.type !== "Condition" || term.name !== name || !term.operands.length)
    {
        return null;
    }
    const [filtered, ...operands] = term.operands;
    return conditionsEqual(filtered, target) ? operands : null;
}

/**
 * The two bounds of a range term, `null` where the term doesn't have one: `ge` alone, the bound it names below, the
 * bound it names above alone, or both combined with `and()`.
 */
function bounds(term: FilterExpression, target: CNode, below: string, above: string): [unknown[] | null, unknown[] | null] | null
{
    if (term.type === "Condition" && term.name === "and" && term.operands.length === 2)
    {
        const from = fieldCondition(term.operands[0], below, target);
        const to = fieldCondition(term.operands[1], above, target);
        return from && to ? [from, to] : null;
    }
    const from = fieldCondition(term, below, target);
    const to = fieldCondition(term, above, target);
    return from || to ? [from, to] : null;
}

/**
 * The instant a day starts at in the time zone.
 */
function startOf(date: Temporal.PlainDate, timeZone: string): Temporal.Instant
{
    return date.toZonedDateTime({timeZone}).toInstant();
}

/**
 * The day an instant starts in the time zone, `null` if it isn't the start of one.
 */
function dayStartingAt(text: string, timeZone: string): Temporal.PlainDate | null
{
    let instant: Temporal.Instant;
    try
    {
        instant = Temporal.Instant.from(text);
    }
    catch (e)
    {
        return null;
    }
    const date = instant.toZonedDateTimeISO(timeZone).toPlainDate();
    return startOf(date, timeZone).equals(instant) ? date : null;
}

/**
 * Two date inputs, from and until.
 */
function DateRangeInput({label, values, setValues}: ColumnFilterInputProps<(string | null)[]>): JSX.Element
{
    const input = (index: number, label: string) => (
        <input type="date" className="qlive-grid-filter-input" aria-label={ label }
               value={ values[index] ?? "" }
               onChange={ ev => {
                   const next = values.slice();
                   next[index] = ev.target.value === "" ? null : ev.target.value;
                   setValues(next);
               } }/>
    );
    return (
        <>
            { input(0, i18n("Filter {0} from", label)) }
            { input(1, i18n("Filter {0} until", label)) }
        </>
    );
}

/**
 * Options for the dateRangeFilter.
 */
export interface DateRangeFilterOptions
{
    /**
     * Time zone whose days a Timestamp column is filtered by. Default: the user's, `Temporal.Now.timeZoneId()`.
     */
    timeZone?: string;
}

/**
 * The filter of a date or time column: two dates, from and until, both included. Either can stay empty for a range
 * open at that end. The inputs hold ISO dates, `"2026-09-28"`, which is what a date input gives.
 *
 * A **Date** column is compared with the dates as they are: `between`, or `ge` or `le` alone. A **Timestamp**
 * column is filtered by the days in a time zone, the user's unless the options name one: from the start of the
 * first day, `ge`, to before the start of the day after the last, `lt`, combined with `and()`. It recognizes only
 * terms on day starts in that zone, so a term written with other instants shows as unclaimed rather than moved to
 * the nearest day.
 *
 * It is the default filter of Date and Timestamp columns.
 *
 * @param scalarType    "Date" or "Timestamp"
 * @param options       time zone of a Timestamp column's days
 */
export function dateRangeFilter(
    scalarType: "Date" | "Timestamp",
    options: DateRangeFilterOptions = {}
): ColumnFilter<(string | null)[]>
{
    if (scalarType !== "Date" && scalarType !== "Timestamp")
    {
        throw new Error("dateRangeFilter() filters Date and Timestamp, not " + JSON.stringify(scalarType) + ".");
    }

    const timeZone = () => options.timeZone ?? Temporal.Now.timeZoneId();

    if (scalarType === "Date")
    {
        return {
            arity: 2,
            partial: true,
            Input: DateRangeInput,

            toCondition(target, [fromText, toText])
            {
                const from = parseDate(fromText);
                const to = parseDate(toText);
                if ((fromText && !from) || (toText && !to))
                {
                    return null;
                }
                const date = (d: Temporal.PlainDate) => value(d.toString(), "Date");
                return (
                    from && to ? condition("between", [target, date(from), date(to)]) :
                    from ? condition("ge", [target, date(from)]) :
                    to ? condition("le", [target, date(to)]) :
                    null
                ) as FilterExpression | null;
            },

            fromCondition(target, term)
            {
                const between = fieldCondition(term, "between", target);
                if (between)
                {
                    const from = operandValue(between[0], "Date");
                    const to = operandValue(between[1], "Date");
                    return between.length === 2 && parseDate(from) && parseDate(to) ? [from, to] : null;
                }
                const found = bounds(term, target, "ge", "le");
                if (!found || found[0] && found[1])
                {
                    return null;
                }
                const [low, high] = found;
                const text = operandValue((low ?? high)![0], "Date");
                if ((low ?? high)!.length !== 1 || !parseDate(text))
                {
                    return null;
                }
                return low ? [text, null] : [null, text];
            }
        };
    }

    return {
        arity: 2,
        partial: true,
        Input: DateRangeInput,

        toCondition(target, [fromText, toText])
        {
            const from = parseDate(fromText);
            const to = parseDate(toText);
            if ((fromText && !from) || (toText && !to))
            {
                return null;
            }
            const zone = timeZone();
            const instant = (d: Temporal.PlainDate) => value(startOf(d, zone).toString(), "Timestamp");
            return and(
                from && condition("ge", [target, instant(from)]) as FilterExpression,
                to && condition("lt", [target, instant(to.add({days: 1}))]) as FilterExpression
            );
        },

        fromCondition(target, term)
        {
            const found = bounds(term, target, "ge", "lt");
            if (!found)
            {
                return null;
            }
            const zone = timeZone();
            const day = (operands: unknown[] | null) => {
                if (!operands)
                {
                    return undefined;
                }
                const text = operandValue(operands[0], "Timestamp");
                return operands.length === 1 && text !== null ? dayStartingAt(text, zone) : null;
            };
            const from = day(found[0]);
            const until = day(found[1]);
            if (from === null || until === null)
            {
                return null;
            }
            return [from?.toString() ?? null, until?.subtract({days: 1}).toString() ?? null];
        }
    };
}
