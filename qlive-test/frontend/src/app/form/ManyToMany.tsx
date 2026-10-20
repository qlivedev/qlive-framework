import { useState } from "react";
import { Form, Layout, useInjection, useWorkingSet, WorkingSet } from "@qlivedev/qlive-ts";
import { Q_Bar, Q_BarResult } from "../bar/Q_Bar";

/**
 * A many-to-many field in a form: Bar.bazes, the Bazes a Bar is associated with through the bar_link table.
 *
 * - **The widget is QLive's**: the layout names no widget for bazes, and a many-to-many field gets the row set.
 *   It lists the associated Bazes, each with a button removing it, and searches the others to add by name --
 *   through the related-rows endpoint, which leaves out the ones already associated.
 * - **The value is the list of rows.** Adding or removing one sets Bar.bazes to the new list; the merge turns
 *   the difference into link rows gained and lost. Nothing here writes a Baz or knows the link table, and the
 *   field carries the merge mark like any other.
 * - **Modes apply as to any field**: below the forms, the first Bar with associations shows the same field
 *   read-only, the rows without buttons or search, and as text, the names joined. Both read the same draft as
 *   the form above, so they follow what is added and removed there.
 */
const EDITABLE: Layout = {
    domainType: "Bar",
    nodes: [
        { field: "name", mode: "text" },
        { field: "bazes" }
    ]
}

const READ_ONLY: Layout = {
    domainType: "Bar",
    nodes: [
        { field: "bazes", mode: "readOnly" }
    ]
}

const TEXT: Layout = {
    domainType: "Bar",
    nodes: [
        { field: "bazes", mode: "text" }
    ]
}

export default function ManyToMany()
{
    const bars: Q_BarResult = useInjection(Q_Bar);

    const [ws] = useState(() => {
        const set = new WorkingSet()
        set.register(bars)
        return set
    })
    const { dirty, merge, undo } = useWorkingSet(ws, { watch: true })
    const shown = bars.rows.find(row => row.bazes.length > 0) ?? bars.rows[0]

    return (
        <div className="form-example">
            <h1>A many-to-many field</h1>
            <div className="toolbar">
                <button className="btn" type="button" disabled={ !dirty } onClick={ () => merge() }>
                    Save
                </button>
                <button className="btn" type="button" disabled={ !dirty } onClick={ undo }>
                    Undo
                </button>
            </div>
            {
                bars.rows.map(row => <Form key={ row.id } row={ ws.edit(row) } layout={ EDITABLE }/>)
            }
            <h2>{ shown.name }, read-only and as text</h2>
            <Form row={ ws.edit(shown) } layout={ READ_ONLY }/>
            <Form row={ ws.edit(shown) } layout={ TEXT }/>
        </div>
    );
}
