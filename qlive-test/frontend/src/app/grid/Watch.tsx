import { DataGrid, useInjection } from "@qlivedev/qlive-ts";
import { Q_FooList, Q_FooListResult } from "./Q_FooList";

/**
 * A list nobody here edits, kept honest by push.
 *
 * With `watch`, the grid learns when somebody else writes a row it shows. Without a working set nothing here is a
 * draft that could clash, so a changed row is only news: the grid marks it and offers to read the rows again.
 *
 * Open /app/grid/editing in a private window, log in as another user there, change a name and save. It has to be
 * another user: a user's own writes are never news to them, from whichever window they came.
 */
export default function Watch()
{
    const foos: Q_FooListResult = useInjection(Q_FooList, {config: {pageSize: 5}});

    return (
        <div className="grid-example">
            <h1>Watching</h1>
            <DataGrid doc={ foos } watch columns={ ["name", "num", "flag", "owner"] }/>
        </div>
    );
}
