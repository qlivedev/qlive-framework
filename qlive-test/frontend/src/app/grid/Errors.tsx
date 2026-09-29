import { DataGrid, unawaited, useInjection } from "@qlivedev/qlive-ts";
import { Q_FooList, Q_FooListResult } from "./Q_FooList";

/**
 * An update() that fails, and what the grid makes of it.
 *
 * The button sorts by a field Foo doesn't have, which the server refuses. The document keeps its rows and config
 * from before and sets its error, which the grid shows above the rows. The next update() that succeeds -- a header
 * click, a filter, the pager -- clears it again.
 *
 * The button doesn't wait for the update() it starts, and its failure is already on the document, so unawaited()
 * lets go of the rejected promise rather than leave it unhandled.
 */
export default function Errors()
{
    const foos: Q_FooListResult = useInjection(Q_FooList, {config: {pageSize: 5}});

    return (
        <div className="grid-example">
            <h1>A failing update</h1>
            <p className="toolbar">
                <button type="button" onClick={ () => unawaited(foos.update({sortFields: ["noSuchField"]})) }>
                    Sort by a field Foo doesn't have
                </button>
            </p>
            <DataGrid doc={ foos } columns={ ["name", "num", "flag", "owner"] }/>
        </div>
    );
}
