import type {JSX, ReactNode} from "react";

import {and, conditionsEqual, Field, FilterExpression} from "../FilterDSL";
import {ColumnFilter, ColumnFilterInputProps} from "./filters";

/**
 * One flag of a flag set filter.
 */
export interface Flag
{
    /** identifies the flag among the set's, and is what the input values hold */
    name: string;

    /** what the checkbox says */
    label: ReactNode;

    /**
     * The term the flag stands for.
     *
     * @param target    what the column filters: a field node, or the expression of a computed column, either with
     *                  the builder methods
     */
    term(target: Field): FilterExpression;
}

/** a flag set filter, with its flags for the input */
interface FlagSetFilter extends ColumnFilter<string[][]>
{
    flags: readonly Flag[];
}

/**
 * A checkbox per flag.
 */
function FlagCheckboxes({values, setValues, filter}: ColumnFilterInputProps<string[][]>): JSX.Element
{
    const checked = values[0] ?? [];
    return (
        <span className="qlive-grid-filter-flags">
            {
                (filter as FlagSetFilter).flags.map(flag => (
                    <label key={ flag.name } className="qlive-grid-filter-flag">
                        <input type="checkbox" checked={ checked.includes(flag.name) }
                               onChange={ ev => {
                                   const next = ev.target.checked
                                       ? [...checked, flag.name]
                                       : checked.filter(name => name !== flag.name);
                                   setValues([next.length ? next : null]);
                               } }/>
                        { flag.label }
                    </label>
                ))
            }
        </span>
    );
}

/**
 * A filter of flags to check: each flag stands for a term, and the rows shown are those matching every flag
 * checked. The input is a checkbox per flag, and its value the names of the checked flags.
 *
 *     const STATE = flagSetFilter([
 *         {name: "open", label: "Open", term: target => target.isNull()},
 *         {name: "mine", label: "Mine", term: () => field("ownerId").eq(value(me))}
 *     ]);
 *
 * A flag's term is up to it: it can test what the column filters, or any other field. The terms of the checked flags are
 * combined with `and()`, in the order of the flags, and a term set from outside is recognized when it is such a
 * combination.
 *
 * @param flags     the flags, in the order they show
 */
export function flagSetFilter(flags: readonly Flag[]): ColumnFilter<string[][]>
{
    const names = new Set(flags.map(flag => flag.name));
    if (names.size !== flags.length)
    {
        throw new Error("flagSetFilter(): two flags have the same name.");
    }

    const filter: FlagSetFilter = {
        arity: 1,
        flags,
        Input: FlagCheckboxes,

        toCondition(target, [checked])
        {
            return and(...flags.filter(flag => checked.includes(flag.name)).map(flag => flag.term(target)));
        },

        fromCondition(target, term)
        {
            const whole = flags.find(flag => conditionsEqual(flag.term(target), term));
            if (whole)
            {
                return [[whole.name]];
            }
            if (term.type !== "Condition" || term.name !== "and")
            {
                return null;
            }
            // the operands in the order of the flags, each flag at most once
            const checked: string[] = [];
            let next = 0;
            for (const operand of term.operands)
            {
                const index = flags.findIndex((flag, i) => i >= next && conditionsEqual(flag.term(target), operand));
                if (index < 0)
                {
                    return null;
                }
                checked.push(flags[index].name);
                next = index + 1;
            }
            return [checked];
        }
    };
    return filter;
}
