import {useEffect, useReducer, useRef} from "react";
import {and, conditionsEqual, FilterExpression, ownedPart, updateComponent} from "../FilterDSL";
import {QueryConfig, QueryConfigDelta} from "../QueryDocument";
import {claimTerms, ColumnFilter, FilterColumn, filled} from "./filters";

/**
 * What useFilters() needs of a query document: its config and update(). A snapshot from useInjection() or
 * useQueryDocument() is one as long as its query selects `config`.
 */
export interface FilterableDocument
{
    config: QueryConfig;
    update(delta: QueryConfigDelta): Promise<unknown>;
}

export interface FiltersOptions
{
    /**
     * Milliseconds between the last change to an input and the update() it causes. Default 300.
     */
    delay?: number;
}

/**
 * The filter state of one column.
 */
export interface ColumnFilterState
{
    /** path of the field the column filters */
    field: string;

    /** the column's filter */
    filter: ColumnFilter<any>;

    /** current input values, `null` for an empty input */
    values: unknown[];

    /**
     * Replaces the input values. The document is updated after the delay, once the inputs have stopped changing, and
     * only if every input is filled; until then the column filters nothing.
     */
    setValues(values: unknown[]): void;

    /** whether the column's term is part of the document's condition */
    active: boolean;
}

/**
 * The filter state of one owner of a condition part.
 */
export interface Filters
{
    /** per column, in the order given */
    columns: ColumnFilterState[];

    /**
     * Terms of the owner's part that no column recognizes: written by code that doesn't know the columns' filters,
     * or recognized by more than one column. They stay in the part until reset().
     */
    unclaimed: FilterExpression[];

    /** whether the owner's part filters anything */
    active: boolean;

    /**
     * Clears every input and the unclaimed terms, and updates the document right away.
     */
    reset(): Promise<void>;
}

/** what a column last wrote and what it holds, outside of React state so the timer sees the latest */
interface ColumnStore
{
    values: unknown[];

    /**
     * First the term the document has for the column, then the terms the column sent since, oldest first. A term
     * coming back that is in here is the column's own, and its input keeps what the user typed.
     */
    sent: (FilterExpression | null)[];
}

interface Store
{
    /** the owned part the store was last synchronized with */
    seen: FilterExpression | null;
    columns: ColumnStore[];
    unclaimed: FilterExpression[];
    timer: ReturnType<typeof setTimeout> | null;
    error: unknown;
}

const DEFAULT_DELAY = 300;

/**
 * The filter inputs of one owner of a condition part: a grid's filter row, a search form, a picker. One hook for all
 * of an owner's filters, because reading a condition back has to see all columns at once to tell whose term is whose.
 *
 *     const filters = useFilters(foos, "foo-grid", [
 *         {field: "name", filter: operatorFilter("containsIgnoreCase")},
 *         {field: "num", filter: operatorFilter("eq", "Int")}
 *     ])
 *
 *     filters.columns.map(column => <FilterInput key={ column.field } column={ column }/>)
 *
 * The owner's part of the condition is its component where the condition is a composition of components, all of it
 * otherwise (see FilterDSL's `ownedPart()` and `updateComponent()`); other owners' components are left as they are.
 * The part is one term per active column, combined with `and()`, plus the unclaimed terms.
 *
 * When the part changes to something the owner didn't write, the inputs show it: each column's filter is asked to
 * recognize a term (`fromCondition`), and what nobody recognizes shows as `unclaimed`. A term the owner wrote
 * itself is not read back, so the input keeps what the user typed even where the filter normalizes it.
 *
 * Changing a filter goes back to the first page.
 *
 * @param doc       query document snapshot
 * @param id        component id of the owner
 * @param columns   the owner's filters, in a fixed order
 * @param options   delay before an input change is sent
 */
export function useFilters(
    doc: FilterableDocument,
    id: string,
    columns: readonly FilterColumn[],
    options: FiltersOptions = {}
): Filters
{
    const [, rerender] = useReducer((n: number) => n + 1, 0);

    const docRef = useRef(doc);
    docRef.current = doc;
    const columnsRef = useRef(columns);
    columnsRef.current = columns;

    const part = ownedPart(doc.config.condition, id);

    const storeRef = useRef<Store | null>(null);
    if (storeRef.current === null || storeRef.current.columns.length !== columns.length)
    {
        const claimed = claimTerms(part, columns);
        storeRef.current = {
            seen: part,
            columns: columns.map((column, index) => ({
                values: claimed.values[index] ?? empty(column),
                sent: [claimed.terms[index]]
            })),
            unclaimed: claimed.unclaimed,
            timer: null,
            error: null
        };
    }
    const store = storeRef.current;

    // Kept rather than cleared once thrown: React renders again after an error before giving up, and the error
    // boundary that catches it unmounts this hook and its store with it.
    if (store.error)
    {
        throw store.error;
    }

    // Idempotent, so a render React throws away and repeats does no harm: the second time the part is the one seen.
    if (!conditionsEqual(part, store.seen))
    {
        readBack(store, part, columns);
    }

    useEffect(() => () => {
        const {timer} = storeRef.current!;
        if (timer)
        {
            clearTimeout(timer);
        }
    }, []);

    const send = () => {
        const current = storeRef.current!;
        current.timer = null;

        const terms = columnsRef.current.map(({field, filter}, index) => {
            const {values, sent} = current.columns[index];
            const term = filled(values) ? filter.toCondition(field, values) : null;
            if (!conditionsEqual(term, sent[sent.length - 1]))
            {
                sent.push(term);
            }
            return term;
        });

        return write(and(...terms, ...current.unclaimed));
    };

    const write = (term: FilterExpression | null) => {
        const {config} = docRef.current;
        let next: FilterExpression | null;
        try
        {
            next = updateComponent(config.condition, id, term);
        }
        catch (e)
        {
            storeRef.current!.error = e;
            rerender();
            return Promise.resolve();
        }
        if (next === config.condition)
        {
            return Promise.resolve();
        }
        return docRef.current.update({condition: next, offset: 0}).then(() => {});
    };

    const schedule = () => {
        const current = storeRef.current!;
        if (current.timer)
        {
            clearTimeout(current.timer);
        }
        current.timer = setTimeout(send, options.delay ?? DEFAULT_DELAY);
    };

    const reset = () => {
        const current = storeRef.current!;
        if (current.timer)
        {
            clearTimeout(current.timer);
        }
        columnsRef.current.forEach((column, index) => current.columns[index].values = empty(column));
        current.unclaimed = [];
        rerender();
        return send();
    };

    const columnStates = columns.map((column, index): ColumnFilterState => {
        const state = store.columns[index];
        return {
            field: column.field,
            filter: column.filter,
            values: state.values,
            setValues: (values: unknown[]) => {
                storeRef.current!.columns[index].values = values.slice();
                rerender();
                schedule();
            },
            active: state.sent[0] !== null
        };
    });

    return {
        columns: columnStates,
        unclaimed: store.unclaimed,
        active: part !== null,
        reset
    };
}

function empty(column: FilterColumn): unknown[]
{
    return new Array(column.filter.arity).fill(null);
}

/**
 * Brings the store in line with an owned part that changed: a column whose term came back as one it sent keeps its
 * input values, any other column shows the term it now has, or nothing.
 */
function readBack(store: Store, part: FilterExpression | null, columns: readonly FilterColumn[])
{
    const claimed = claimTerms(part, columns);

    columns.forEach((column, index) => {
        const state = store.columns[index];
        const term = claimed.terms[index];
        const own = state.sent.findIndex(t => conditionsEqual(t, term));
        if (own >= 0)
        {
            state.sent = state.sent.slice(own);
        }
        else
        {
            state.values = claimed.values[index] ?? empty(column);
            state.sent = [term];
        }
    });

    store.unclaimed = claimed.unclaimed;
    store.seen = part;
}
