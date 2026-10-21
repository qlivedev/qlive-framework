import { useState } from "react";
import {
    FilterDSL,
    Form,
    i18n,
    Layout,
    useFormContext,
    useInjection,
    useWorkingSet,
    WorkingSet
} from "@qlivedev/qlive-ts";

const { field, or, value } = FilterDSL
import { Q_FooList, Q_FooListResult } from "../grid/Q_FooList";

/**
 * A form from a layout: which fields, grouped how, with which widget and in which mode, written as data.
 *
 * - **Sections** group fields under a legend, the section's text. Translated here, through i18n() calls written
 *   out, so the analysis finds their tags.
 * - **A widget by name**: description is edited with the text area main.tsx registers as "textarea". A layout in
 *   TSX could hold the component itself; the name is what a layout from the server would use.
 * - **A widget override**: num names no widget, and gets the stepper main.tsx configures for Foo.num.
 * - **Modes**: created is read-only, the owner shows as text -- the related row's name, not its key.
 * - **A field condition** narrows what a picker offers, written against the form's row: TYPE_D is for foos without
 *   the flag. FooType is a catalog, so the picker tests its rows in the browser; tick the flag and TYPE_D is gone
 *   from the list at once.
 * - **A component of the application's own** sits in the layout like a field and reads the form's context: the
 *   lock below changes the mode of the name field while the form is open.
 */
const FOO_LAYOUT: Layout = {
    domainType: "Foo",
    nodes: [
        {
            section: i18n("Foo.identity"),
            nodes: [
                { field: "name" },
                { component: NameLock },
                { field: "description", widget: "textarea" }
            ]
        },
        {
            section: i18n("Foo.details"),
            nodes: [
                { field: "num" },
                { field: "flag" },
                {
                    field: "fooType",
                    condition: or(field("flag").eq(value(false)), field("fooType.name").ne(value("TYPE_D")))
                },
                { field: "created", mode: "readOnly" },
                { field: "owner", mode: "text" }
            ]
        }
    ]
}

/**
 * Locks the name field, through the context of the form it is rendered in.
 */
function NameLock()
{
    const { modes, setMode } = useFormContext()
    const locked = modes.get("name") === "readOnly"

    return (
        <label className="name-lock">
            <input type="checkbox" checked={ locked } onChange={ () => setMode("name", locked ? "normal" : "readOnly") }/>
            lock the name
        </label>
    )
}

export default function LayoutExample()
{
    const foos: Q_FooListResult = useInjection(Q_FooList, { config: { pageSize: 2 } });

    const [ws] = useState(() => {
        const set = new WorkingSet()
        set.register(foos)
        return set
    })
    const { dirty, merge, undo } = useWorkingSet(ws, { watch: true })

    return (
        <div className="form-example">
            <h1>A layout</h1>
            <div className="toolbar">
                <button className="btn" type="button" disabled={ !dirty } onClick={ () => merge() }>
                    Save
                </button>
                <button className="btn" type="button" disabled={ !dirty } onClick={ undo }>
                    Undo
                </button>
            </div>
            {
                foos.rows.map(row => <Form key={ row.id } row={ ws.edit(row) } layout={ FOO_LAYOUT }/>)
            }
        </div>
    );
}
