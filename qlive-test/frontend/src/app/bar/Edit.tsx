import { useState } from "react";
import {
    ConflictResolution,
    Form,
    Field,
    FormStatus,
    MergeView,
    useInjection,
    useWorkingSet,
    WorkingSet
} from "@qlivedev/qlive-ts";
import { Q_Bar, Q_BarResult } from "./Q_Bar";

/**
 * Editing rows through a working set: scalar fields, a many-to-many, and the resolution of a clash in the
 * form the user was already looking at.
 *
 * The row forms are QLive's Form with the application's choice of fields in it, the many-to-many Bar.bazes
 * among them. The conflict path is worth trying twice in two browser windows:
 * save in one, then save in the other.
 *
 * With push wired in, the second window does not have to save to find out. A field somebody else changed is
 * marked while the user is still typing, which is the same marking the merge does, arriving early rather
 * than late -- so what the two windows show is a notice before the save rather than a conflict after it.
 */

/**
 * One row as the query yields it, read off the query's own result type rather than written out again beside
 * it: that type is generated from the selection, so a hand-written copy of it is a copy that goes stale.
 */
type EditRow = Q_BarResult["rows"][number]

/**
 * The fields the form renders. A Form without children would derive them from the row, but this one gives
 * every field the conflict buttons, which is what children are for. bazes is a many-to-many field: its widget
 * lists the associated Bazes and searches the others to add, and the merge turns the difference into
 * associations gained and lost -- nothing here writes a Baz or knows the link table.
 */
const FIELDS: Array<keyof EditRow & string> = ["name", "num", "description", "bazes"]

const VIEWS: MergeView[] = ["merged", "mine", "stored"]


export default function Edit()
{
    const bars: Q_BarResult = useInjection(Q_Bar);


    // Made once and registered at once: the working set lives as long as the editing does, and a view that
    // rendered before its rows were registered could not edit them. A merge that lands refreshes the
    // document and registers what comes back, so this happens exactly here and nowhere else.
    const [ws] = useState(() => {
        const set = new WorkingSet()
        set.register(bars)
        return set
    })

    // One flag, and other people's writes land in the working set as they happen -- a field somebody else
    // changed is marked while this user is still typing, rather than at save time. Nothing is added to
    // what comes back: what changes is the working set, and this is what re-renders for it.
    const { dirty, conflicts, view, merge, undo, setView } = useWorkingSet(ws, { watch: true })

    return (
        <div className="bar-edit">
            <h1>Bars</h1>

            <div className="toolbar">
                <button className="btn" type="button" disabled={ !dirty } onClick={ () => merge() }>
                    Save
                </button>
                <button className="btn" type="button" disabled={ !dirty } onClick={ undo }>
                    Undo
                </button>

                {
                    // One flag for the whole form. No input below knows about it: a draft read is what
                    // returns the user's own value, the stored one, or the two folded together.
                    VIEWS.map(name => (
                        <label key={ name } className="view">
                            <input
                                type="radio"
                                name="merge-view"
                                checked={ view === name }
                                onChange={ () => setView(name) }
                            />
                            { name }
                        </label>
                    ))
                }
            </div>

            {
                conflicts.length > 0 && (
                    <p className="warning">
                        Somebody saved { conflicts.length === 1 ? "this row" : "these rows" } while you were
                        editing. Your values are the ones standing -- look at the marked fields and save
                        again.
                    </p>
                )
            }

            {
                bars.rows.map(row => <BarForm key={ row.id } ws={ ws } row={ row }/>)
            }
        </div>
    );
}


/**
 * One row. The draft is read on every render rather than kept in state -- it is not the row, and the
 * working set is what holds it.
 *
 * The fields are QLive's: a Field turns what is typed into a value of the field's type, keeps text that
 * isn't one with an error, and carries the merge mark. The conflict buttons are QLive's too, given here as the
 * decorator -- a form rendering a layout applies them by itself, a hand-written one says where it wants them.
 *
 * FormStatus places the form's live region below the fields, where it would otherwise sit above them. QLive keeps
 * it for screen readers; the app's CSS shows it, so what somebody else changed is said in words here too.
 */
function BarForm({ ws, row }: {
    ws: WorkingSet,
    row: EditRow
})
{
    return (
        <div className="domain-type">
            <h1>{ row.id }</h1>

            <Form row={ ws.edit(row) }>
                <div className="qlive-form">
                    {
                        FIELDS.map(name => <Field key={ name } path={ name } decorator={ ConflictResolution }/>)
                    }
                </div>
                <FormStatus/>
            </Form>

            <button className="btn" type="button" onClick={ () => ws.delete(row) }>
                Delete
            </button>
        </div>
    )
}

