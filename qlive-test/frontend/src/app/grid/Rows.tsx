import { useState } from "react";
import { DataGrid, Temporal, useInjection, useWorkingSet, WorkingSet } from "@qlivedev/qlive-ts";
import { Q_FooList, Q_FooListResult } from "./Q_FooList";

type FooRow = Q_FooListResult["rows"][number];

/**
 * Adding and removing rows.
 *
 * A row created in the working set shows on the first page, before the document's rows, as long as the grid's
 * condition matches it: filter the names by "copy" and the copies stay, filter by anything else and they go. A
 * deleted row stays in the list, marked, until it is saved or undone.
 */
export default function Rows()
{
    const foos: Q_FooListResult = useInjection(Q_FooList, {config: {pageSize: 5}});

    const [ws] = useState(() => new WorkingSet());
    const {dirty, merge, undo} = useWorkingSet(ws);

    return (
        <div className="grid-example">
            <h1>New and deleted rows</h1>
            <div className="toolbar">
                <button className="btn" type="button" disabled={ !dirty } onClick={ () => merge() }>
                    Save
                </button>
                <button className="btn" type="button" disabled={ !dirty } onClick={ undo }>
                    Undo
                </button>
            </div>
            <DataGrid
                doc={ foos }
                workingSet={ ws }
                columns={ [
                    "name",
                    "num",
                    "owner",
                    {heading: "", render: row => <RowActions ws={ ws } row={ row }/>}
                ] }
            />
        </div>
    );
}

/**
 * A new row is a copy of an existing one, which is where the fields the table requires and the grid doesn't show
 * come from.
 */
function RowActions({ws, row}: { ws: WorkingSet, row: FooRow })
{
    return (
        <span className="grid-example-actions">
            <button className="btn" type="button" onClick={ () => ws.create<FooRow>("Foo", {
                name: row.name + " (copy)",
                num: row.num,
                flag: row.flag,
                type: row.type,
                ownerId: row.ownerId,
                created: Temporal.Now.instant()
            }) }>
                Duplicate
            </button>
            <button className="btn" type="button" onClick={ () => ws.delete(row) }>
                Delete
            </button>
        </span>
    );
}
