import { DataGrid, FilterInput, flagSetFilter, patternFilter, useFilters, useInjection } from "@qlivedev/qlive-ts";
import { field, value } from "@qlivedev/qlive-ts/filter";
import { Q_FooList, Q_FooListResult } from "./Q_FooList";

/**
 * The search form's filters. Declared outside the view: useFilters() goes by their order, and a filter is a value
 * like any other.
 */
const SEARCH = [
    {field: "description", filter: patternFilter()},
    {
        field: "flag",
        filter: flagSetFilter([
            {name: "flagged", label: "Flagged", term: target => target.isTrue()},
            {name: "large", label: "Num over 1000", term: () => field("num").gt(value(1000))}
        ])
    }
];

/**
 * A search form above the grid, filtering the same rows.
 *
 * The document has one condition and two owners of it: the form writes the component "search", the grid's filter
 * row the component "grid", and the condition is both joined with and(). Each narrows what the other shows, and
 * neither touches the other's part -- clear the search and the grid's filters stay.
 */
export default function Search()
{
    const foos: Q_FooListResult = useInjection(Q_FooList, {config: {pageSize: 5}});

    const search = useFilters(foos, "search", SEARCH);
    const [description, flags] = search.columns;

    return (
        <div className="grid-example">
            <h1>A search form</h1>
            <form className="grid-example-search" onSubmit={ ev => ev.preventDefault() }>
                <label>
                    Description matches
                    <FilterInput column={ description }/>
                </label>
                <FilterInput column={ flags }/>
                <button type="button" className="btn" disabled={ !search.active } onClick={ () => search.reset() }>
                    Clear search
                </button>
            </form>
            <DataGrid doc={ foos } columns={ ["name", "num", "flag", "owner"] }/>
        </div>
    );
}
