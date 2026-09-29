import { DataGrid, useInjection } from "@qlivedev/qlive-ts";
import { Q_FooList, Q_FooListResult } from "./Q_FooList";

/**
 * The least a grid takes: a query document and the fields to show.
 *
 * Everything else is derived from the schema. The headings are the i18n() of `Foo.name` and so on, each value is
 * formatted as its type, a header click sorts by the column, the filter row offers what fits the type -- text
 * contains, a yes/no select, a number, a date range -- and the pager pages. The owner is a relation and shows its
 * name field, the login.
 *
 * None of it is the grid's own state. Sort, filter and page are the document's config, and the grid changes them
 * through update(), so anything else in the view holding the same document sees the same rows.
 */
export default function Basic()
{
    const foos: Q_FooListResult = useInjection(Q_FooList, {config: {pageSize: 5}});

    return (
        <div className="grid-example">
            <h1>A plain grid</h1>
            <DataGrid doc={ foos } columns={ ["name", "num", "flag", "owner", "created"] }/>
        </div>
    );
}
