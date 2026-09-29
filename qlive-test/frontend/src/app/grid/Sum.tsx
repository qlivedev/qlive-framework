import { dateRangeFilter, DataGrid, FilterInput, useFilters, useInjection } from "@qlivedev/qlive-ts";
import { field } from "@qlivedev/qlive-ts/filter";
import { Q_QuuxList, Q_QuuxListResult } from "./Q_QuuxList";

/**
 * The sum the computed column shows, as the server sorts by it.
 */
const SUM = field("numA").add(field("numB"));

/**
 * The search form's one filter: the days the Quux was created on.
 */
const SEARCH = [
    {field: "created", filter: dateRangeFilter("Timestamp")}
];

/**
 * A column computed from two fields, sorted by the expression that computes it, under a date range form.
 *
 * A click on the sum's header sorts by it ascending, the next one descending -- an expression toggles like a field.
 * The form narrows the rows to a range of days, from or until alone for a range open at the other end.
 */
export default function Sum()
{
    const quuxes: Q_QuuxListResult = useInjection(Q_QuuxList, {config: {sortFields: ["name"]}});

    const search = useFilters(quuxes, "search", SEARCH);
    const [created] = search.columns;

    return (
        <div className="grid-example">
            <h1>Sorted by a sum</h1>
            <form className="grid-example-search" onSubmit={ ev => ev.preventDefault() }>
                <label>
                    Created from, until
                    <span className="grid-example-range">
                        <FilterInput column={ created }/>
                    </span>
                </label>
                <button type="button" className="btn" disabled={ !search.active } onClick={ () => search.reset() }>
                    Clear search
                </button>
            </form>
            <DataGrid
                doc={ quuxes }
                columns={ [
                    "name",
                    "numA",
                    "numB",
                    // computed in the browser, sorted on the server by the same sum
                    {
                        heading: "numA + numB",
                        render: row => row.numA + row.numB,
                        sort: SUM
                    },
                    // the form filters it already
                    {field: "created", nowrap: true, filter: false}
                ] }
            />
        </div>
    );
}
