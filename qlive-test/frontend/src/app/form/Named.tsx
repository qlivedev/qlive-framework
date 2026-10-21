import { useState } from "react";
import { Form, useInjection, useWorkingSet, WorkingSet } from "@qlivedev/qlive-ts";
import { Q_FooList, Q_FooListResult } from "../grid/Q_FooList";

/**
 * A layout by its name: `<Form row={ draft } layout="fooSummary"/>`.
 *
 * The server delivers fooSummary with the bootstrap: the backend's LayoutService reads it from layouts.json, with
 * name, num and the owner as text. main.tsx's init hook adds the description, edited with the text area, to the
 * end -- the server's layouts and the application's share one namespace, and the application may change any of
 * them before a view renders.
 */
export default function Named()
{
    const foos: Q_FooListResult = useInjection(Q_FooList, { config: { pageSize: 3 } });

    const [ws] = useState(() => {
        const set = new WorkingSet()
        set.register(foos)
        return set
    })
    const { dirty, merge, undo } = useWorkingSet(ws, { watch: true })

    return (
        <div className="form-example">
            <h1>A named layout</h1>
            <div className="toolbar">
                <button className="btn" type="button" disabled={ !dirty } onClick={ () => merge() }>
                    Save
                </button>
                <button className="btn" type="button" disabled={ !dirty } onClick={ undo }>
                    Undo
                </button>
            </div>
            {
                foos.rows.map(row => <Form key={ row.id } row={ ws.edit(row) } layout="fooSummary"/>)
            }
        </div>
    );
}
