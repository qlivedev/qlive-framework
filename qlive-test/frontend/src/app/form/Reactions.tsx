import { useState } from "react";
import {
    Field,
    FilterDSL,
    Form,
    Reaction,
    useInjection,
    useWorkingSet,
    WorkingSet
} from "@qlivedev/qlive-ts";
import { Q_FooList, Q_FooListResult } from "../grid/Q_FooList";
import { Q_QuuxForms, Q_QuuxFormsResult } from "./Q_QuuxForms";

const { field } = FilterDSL

/**
 * What Quux's forms do, written in TSX and handed to a form with children.
 *
 * - **On blur**: leaving numA above numB lifts numB to it, which is what Quux's invariant asks for. Not on every
 *   change, which would lift numB at the first digit of a larger number.
 * - **On an error**: while the invariant blames numB, the name is read-only, so the numbers are fixed first. The
 *   reaction to the error clearing gives it back.
 */
const QUUX_REACTIONS: Reaction[] = [
    {
        on: [ { blur: "numA" } ],
        when: field("numA").gt(field("numB")),
        effects: [ { field: "numB", value: field("numA") } ]
    },
    { on: [ { errorAt: "numB" } ], effects: [ { field: "name", mode: "readOnly" } ] },
    { on: [ { errorAt: "numB", cleared: true } ], effects: [ { field: "name", mode: "normal" } ] }
]

/**
 * Reactions: what a form does when a field changes or loses focus, or an error appears, to help the user bring the
 * row into compliance with the rules. They are data, evaluated by the form; the server never runs them, and the
 * values they set reach it as changes like the user's own.
 *
 * The foos render fooReactions, which the server delivers (layouts.json) and checks at startup:
 *
 * - **A mode toggle**: the description is open only for flagged foos, from the moment the form opens -- the
 *   reaction answers `init` as well as a change of the flag.
 * - **A reset**: flagging a foo of TYPE_D, which the Layout example's picker no longer offers it, sets TYPE_A.
 * - **A lookup**: a change of num describes it, through the effect main.tsx registers as "describeNum", which
 *   takes its time. Type quickly and only the last number's description arrives. Type a description of your own
 *   and it stays, until num changes again.
 *
 * The quuxes are written by hand, with their reactions given to the form (QUUX_REACTIONS above).
 *
 * Undo takes back what the reactions set along with what the user typed, and forgets that they set it.
 */
export default function Reactions()
{
    const foos: Q_FooListResult = useInjection(Q_FooList, { config: { pageSize: 2 } });
    const quuxes: Q_QuuxFormsResult = useInjection(Q_QuuxForms, { config: { pageSize: 2 } });

    const [ws] = useState(() => {
        const set = new WorkingSet()
        set.register(foos)
        set.register(quuxes)
        return set
    })
    const { dirty, merge, undo } = useWorkingSet(ws, { watch: true })

    return (
        <div className="form-example">
            <h1>Reactions</h1>
            <div className="toolbar">
                <button className="btn" type="button" disabled={ !dirty } onClick={ () => merge() }>
                    Save
                </button>
                <button className="btn" type="button" disabled={ !dirty } onClick={ undo }>
                    Undo
                </button>
            </div>
            <h2>From the server</h2>
            {
                foos.rows.map(row => <Form key={ row.id } row={ ws.edit(row) } layout="fooReactions"/>)
            }
            <h2>Written by hand</h2>
            {
                quuxes.rows.map(row => (
                    <Form key={ row.id } row={ ws.edit(row) } reactions={ QUUX_REACTIONS }>
                        <div className="qlive-form">
                            <Field path="name"/>
                            <Field path="numA"/>
                            <Field path="numB"/>
                        </div>
                    </Form>
                ))
            }
        </div>
    );
}
