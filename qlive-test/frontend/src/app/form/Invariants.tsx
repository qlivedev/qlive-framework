import { useState } from "react";
import { Form, Temporal, useInjection, useWorkingSet, WorkingSet } from "@qlivedev/qlive-ts";
import { Q_QuuxForms, Q_QuuxFormsResult } from "./Q_QuuxForms";

/**
 * Rules on a type: Quux's invariants, which hold for every write of its rows, whoever writes. Its first number is
 * not above its second, and it has a name. QLiveDomainConfiguration attaches them with a RulesMetadataProvider, a
 * rule class and a lambda, and they reach the client in the bootstrap.
 *
 * Nothing here names them. The working set checks a row against them before a save sends it, and the form shows
 * a broken one at the field it blames: at once where the user changed a field the rule reads, and the rest after a
 * save was refused, so a new quux doesn't open full of errors. The server checks the same rules, for every writer.
 *
 * An update sends the fields an invariant it touches reads, changed or not: changing numA sends numB along, which
 * is why the query selects both.
 *
 * Worth trying:
 *  - a numA above numB, which is refused at numB as you type
 *  - "New quux" and Save straight away: both rules are refused, at the fields that are still empty
 *  - clearing a name, then Undo
 */
export default function Invariants()
{
    const quuxes: Q_QuuxFormsResult = useInjection(Q_QuuxForms, { config: { pageSize: 3 } });

    const [ws] = useState(() => {
        const set = new WorkingSet()
        set.register(quuxes)
        return set
    })
    const { dirty, refusals, merge, undo } = useWorkingSet(ws, { watch: true })

    return (
        <div className="form-example">
            <h1>Rules on a type</h1>
            <div className="toolbar">
                <button className="btn" type="button" disabled={ !dirty } onClick={ () => merge() }>
                    Save
                </button>
                <button className="btn" type="button" disabled={ !dirty } onClick={ undo }>
                    Undo
                </button>
                <button
                    className="btn"
                    type="button"
                    onClick={ () => ws.create("Quux", { created: Temporal.Now.instant() }) }
                >
                    New quux
                </button>
            </div>
            {
                refusals.length > 0 && (
                    <p className="warning">Nothing was saved: correct the marked fields and save again.</p>
                )
            }
            {
                ws.created("Quux").map(draft => <Form key={ draft.id } row={ draft }/>)
            }
            {
                quuxes.rows.map(row => <Form key={ row.id } row={ ws.edit(row) }/>)
            }
        </div>
    );
}
