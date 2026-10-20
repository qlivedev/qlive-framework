import { useState } from "react";
import { Form, useInjection, useWorkingSet, WorkingSet } from "@qlivedev/qlive-ts";
import { Q_FooList, Q_FooListResult } from "../grid/Q_FooList";

/**
 * A form nobody wrote: `<Form row={ draft }/>` and nothing else.
 *
 * The layout is derived from the row, so the fields are the ones the query selected, in its order. Every field
 * gets the widget for its type -- a text input, a number, a checkbox, a date-time picker -- and the ones no form
 * edits render as text: the id, the version, a computed field. A foreign key and its relation object are one
 * field, the relation's, edited with a picker: typing searches the users by login, on the server, and choosing one
 * sets ownerId.
 *
 * Foo.num is the exception: main.tsx overrides its widget for every form that doesn't name one, this one included.
 *
 * Typing "12x" into num shows what happens to text that isn't a number: it stays as typed, with an error, and
 * never reaches the working set. Worth trying in two windows too: a derived form puts the conflict buttons next
 * to any field both saves changed.
 */
export default function Derived()
{
    const foos: Q_FooListResult = useInjection(Q_FooList, { config: { pageSize: 3 } });

    const [ws] = useState(() => {
        const set = new WorkingSet()
        set.register(foos)
        return set
    })
    const { dirty, conflicts, merge, undo } = useWorkingSet(ws, { watch: true })

    return (
        <div className="form-example">
            <h1>A derived form</h1>
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
                        Somebody saved rows you changed. Your values are the ones standing -- choose at the marked
                        fields and save again.
                    </p>
                )
            }
            {
                foos.rows.map(row => <Form key={ row.id } row={ ws.edit(row) }/>)
            }
        </div>
    );
}
