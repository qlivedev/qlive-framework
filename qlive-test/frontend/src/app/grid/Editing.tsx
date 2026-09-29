import { useState } from "react";
import { DataGrid, useInjection, useWorkingSet, WorkingSet } from "@qlivedev/qlive-ts";
import { Q_FooList, Q_FooListResult } from "./Q_FooList";

/**
 * Editing rows in place.
 *
 * The rows are edited in a working set, which lives as long as the editing does. Given one, the grid shows its
 * drafts instead of the document's rows: a render function gets the draft, so an input writes into the working set,
 * and a changed cell is marked. The drafts stay across page turns, sorting and filtering until they are saved or
 * undone.
 *
 * Worth trying in two windows: change the same row in both and save in one, then in the other. The second save
 * meets the first as a conflict, and the marked row shows which of its values are standing.
 */
export default function Editing()
{
    const foos: Q_FooListResult = useInjection(Q_FooList, {config: {pageSize: 5}});

    const [ws] = useState(() => new WorkingSet());
    const {dirty, conflicts, merge, undo} = useWorkingSet(ws);

    return (
        <div className="grid-example">
            <h1>Editing</h1>
            <div className="toolbar">
                <button className="btn" type="button" disabled={ !dirty } onClick={ () => merge() }>
                    Save
                </button>
                <button className="btn" type="button" disabled={ !dirty } onClick={ undo }>
                    Undo
                </button>
            </div>
            {
                conflicts.length > 0 && (
                    <p className="warning">
                        Somebody saved rows you changed. Your values are the ones standing -- look at the marked
                        rows and save again.
                    </p>
                )
            }
            <DataGrid
                doc={ foos }
                workingSet={ ws }
                columns={ [
                    {
                        field: "name",
                        render: row => <input value={ row.name } onChange={ ev => { row.name = ev.target.value } }/>
                    },
                    {
                        field: "num",
                        // an input gives a string, and num is an Int
                        render: row => (
                            <input type="number" value={ row.num }
                                   onChange={ ev => { row.num = Number(ev.target.value) } }/>
                        )
                    },
                    {
                        field: "flag",
                        render: row => (
                            <input type="checkbox" checked={ row.flag }
                                   onChange={ ev => { row.flag = ev.target.checked } }/>
                        )
                    },
                    "owner"
                ] }
            />
        </div>
    );
}
