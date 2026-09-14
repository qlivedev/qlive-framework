import { useState } from "react";
import {
    MergeAccessor,
    MergeView,
    useInjection,
    useMerge,
    useWorkingSet,
    WorkingSet
} from "@quinscape/qlive-ts";
import { Q_Bar, Q_BarResult } from "./Q_Bar";
import { Q_BazList, Q_BazListResult } from "./Q_BazList";

/**
 * Editing rows through a working set: scalar fields, a many-to-many, and the resolution of a clash in the
 * form the user was already looking at.
 *
 * Nothing below is a framework component. QLive hands out the state -- what changed, what clashed, which
 * class a field carries -- and the application renders the inputs, which is the whole of the split. The
 * conflict path is worth trying twice in two browser windows: save in one, then save in the other.
 *
 * With push wired in, the second window does not have to save to find out. A field somebody else changed is
 * marked while the user is still typing, which is the same marking the merge does, arriving early rather
 * than late -- so what the two windows show is a notice before the save rather than a conflict after it.
 */

/**
 * One row as the query yields it, and one association as the form writes one back.
 *
 * Both are read off the query's own result type rather than written out again beside it: that type is
 * generated from the selection, so a hand-written copy of it is a copy that goes stale. The link is the
 * one place the two differ -- a new association is written as the row it is about, `{ baz }` and nothing
 * else, and the merge turns that into a BarLink insert with both foreign keys.
 */
type QueriedLink = Q_BarResult["rows"][number]["bazLinks"][number]

type EditLink = Partial<QueriedLink> & Pick<QueriedLink, "baz">

type EditRow = Omit<Q_BarResult["rows"][number], "bazLinks"> & { bazLinks: EditLink[] }

/**
 * The fields the form renders, which could as well come from the schema or from a config. Nothing below is
 * written per field, and that is what an accessor buys over a hook per field.
 */
const FIELDS: Array<keyof EditRow> = ["name", "num", "description"]

const VIEWS: MergeView[] = ["merged", "mine", "stored"]


/**
 * What goes into the draft for one field. An input hands over a string whatever the field is, and a working
 * set records the value it is given -- Bar.num is an Int, and "3" is not one.
 */
function typed(name: keyof EditRow, value: string): string | number
{
    return name === "num" ? Number(value) : value
}


/**
 * The id of the Baz one link is about, from the foreign key or from the row itself. An association just
 * added by the form has only the row, which is the short form the merge takes as well.
 */
function bazIdOf(link: EditLink): string
{
    return link.bazId ?? link.baz.id
}


export default function Edit()
{
    const bars: Q_BarResult = useInjection(Q_Bar);

    // The one config this view spells out, and the shape of the exception: bazes are the options of a
    // picker rather than a page to leaf through, so this view needs all of them at once. That is a fact
    // about this form and not about the type, so it is said here and the domain's default is left alone.
    const bazes: Q_BazListResult = useInjection(Q_BazList, { config: { pageSize: 50 } });

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
                bars.rows.map(row => <BarForm key={ row.id } ws={ ws } row={ row } bazes={ bazes.rows }/>)
            }
        </div>
    );
}


/**
 * One row. The draft is read on every render rather than kept in state -- it is not the row, and the
 * working set is what holds it.
 */
function BarForm({ ws, row, bazes }: {
    ws: WorkingSet,
    row: EditRow,
    bazes: Q_BazListResult["rows"]
})
{
    const bar = ws.edit(row)
    const merge = useMerge(bar)

    return (
        <div className="domain-type">
            <h1>{ row.id }</h1>

            {
                FIELDS.map(name => {

                    const field = merge.field(name)

                    return (
                        <div className="field" key={ name }>
                            <label htmlFor={ row.id + "-" + name }>{ name }</label>

                            <div className={ "field-control " + field.className }>
                                <input
                                    id={ row.id + "-" + name }
                                    value={ (bar[name] as string | number | null) ?? "" }
                                    onChange={ e => { (bar as any)[name] = typed(name, e.target.value) } }
                                />
                            </div>

                            {
                                // Nothing is undecided and nothing blocks: the user's value already stands,
                                // and these two buttons are how they say otherwise.
                                field.status === "conflict" && field.storedKnown && (
                                    <span className="resolve">
                                        <button
                                            className="btn"
                                            type="button"
                                            onClick={ () => field.resolve("mine") }
                                        >
                                            yours: { String(field.mine) }
                                        </button>
                                        <button
                                            className="btn"
                                            type="button"
                                            onClick={ () => field.resolve("stored") }
                                        >
                                            saved: { String(field.stored) }
                                        </button>
                                    </span>
                                )
                            }

                            {
                                // A push message says which fields changed and carries no values, so there
                                // is no second value to offer yet. Saving asks the server, which answers
                                // with both -- and the two buttons above appear then.
                                field.status === "conflict" && !field.storedKnown && (
                                    <span className="resolve">somebody else changed this too</span>
                                )
                            }
                        </div>
                    )
                })
            }

            <Associations bar={ bar } bazes={ bazes } merge={ merge }/>

            <button className="btn" type="button" onClick={ () => ws.delete(row) }>
                Delete
            </button>
        </div>
    )
}


/**
 * The many-to-many. A link array is set to the associations the row is to have, and the merge turns the
 * difference into BarLink inserts and deletions -- nothing here writes a Baz, and nothing here has to know
 * that BarLink exists beyond naming the rows it points at.
 */
function Associations({ bar, bazes, merge }: {
    bar: EditRow,
    bazes: Q_BazListResult["rows"],
    merge: MergeAccessor
})
{
    const links = bar.bazLinks
    const linked = new Set(links.map(bazIdOf))

    return (
        <div className="field">
            <label>bazLinks</label>

            <div className={ "associations " + merge.field("bazLinks").className }>
                {
                    bazes.map(baz => (
                        <label key={ baz.id }>
                            <input
                                type="checkbox"
                                checked={ linked.has(baz.id) }
                                onChange={ e => {
                                    bar.bazLinks = e.target.checked
                                        ? [...links, { baz }]
                                        : links.filter(link => bazIdOf(link) !== baz.id)
                                } }
                            />
                            { baz.name }
                        </label>
                    ))
                }
            </div>
        </div>
    )
}
