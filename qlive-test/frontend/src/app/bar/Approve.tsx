import { useEffect, useMemo, useState } from "react";
import {
    ActionDraft,
    ActionRefusals,
    Field,
    Form,
    formatValue,
    refusalMessage,
    useActionDraft,
    useInjection,
    useRefusals
} from "@qlivedev/qlive-ts";
import { Q_BarApprove, Q_BarApproveResult } from "./Q_BarApprove";

/**
 * Actions: approving bars through BarLogic's approveBar and approveBars, each held as an action draft.
 *
 * The selected rows are the drafts' subjects as they are -- the action sends each one's id and version, and the
 * server loads the row as it is stored. The comment and the notify flag are the actions' input, edited in a form of
 * the action's parameters alone, with no row of its own. Nothing here checks a rule: the server does, and a refusal
 * comes back with one reason per broken rule, each blaming a field by its path. The form's fields show the reasons
 * blaming them, each selected row those blaming it, and ActionRefusals whatever is left.
 *
 * The action writes the approval, which gives each row a new version, so the rows are read again after it:
 * approving one again is then no conflict.
 *
 * Worth trying:
 *  - a bar without a description, which the stored-state rule refuses, at its row
 *  - an empty comment, which keeps Approve disabled: the comment is required, and blank text is none
 *  - several bars at once, one of them without a description: none is approved, and the reason names its row
 *  - /bar/edit in another window: change a bar there and approve it here without reloading, which is a conflict
 */
type Row = Q_BarApproveResult["rows"][number]

export default function Approve()
{
    const bars: Q_BarApproveResult = useInjection(Q_BarApprove, { config: { pageSize: 20 } });

    const approve = useActionDraft("approveBar")
    const approveAll = useActionDraft("approveBars")

    const [selected, setSelected] = useState<string[]>([])

    // the rows in the order they were selected, which is the order the request sends them in
    const rows = useMemo(
        () => selected.map(id => bars.rows.find(row => row.id === id)).filter((row): row is Row => !!row),
        [selected, bars.rows]
    )

    // one bar is approveBar's, several are approveBars'
    const bulk = rows.length > 1
    const action = bulk ? approveAll : approve

    const { set: setBar } = approve
    const { set: setBars } = approveAll
    useEffect(() => {
        setBar("bar", rows.length === 1 ? rows[0] : null)
        setBars("bars", rows)
    }, [rows, setBar, setBars])

    const toggle = (id: string) => {
        const next = selected.includes(id) ? selected.filter(s => s !== id) : [...selected, id]
        // the comment goes along when the selection moves from one action to the other
        const comment = action.args.comment
        if (comment != null && next.length > 1 !== bulk)
        {
            next.length > 1 ? approveAll.set("comment", comment) : approve.set("comment", comment)
        }
        setSelected(next)
    }

    const issue = async () => {
        const outcome = await action.issue()
        if (!outcome.refused)
        {
            bars.update({})
        }
    }

    const result = action.outcome && !action.outcome.refused ? action.outcome.result : null

    return (
        <div className="bar-approve">
            <h1>Approve bars</h1>

            <table className="rows">
                <thead>
                    <tr>
                        <th/>
                        <th>name</th>
                        <th>description</th>
                        <th>approved</th>
                        <th/>
                    </tr>
                </thead>
                <tbody>
                    {
                        bars.rows.map(row => (
                            <BarRow
                                key={ row.id }
                                row={ row }
                                action={ action }
                                // the single action names its subject "bar", the bulk one "bars", with the position
                                path={ !selected.includes(row.id) ? null : bulk ? "bars." + rows.indexOf(row) : "bar" }
                                toggle={ toggle }
                            />
                        ))
                    }
                </tbody>
            </table>

            <Form key={ action.name } action={ action }>
                <Field path="comment"/>
                { !bulk && <Field path="notify"/> }
            </Form>

            <ActionRefusals action={ action }/>

            <div className="toolbar">
                <button className="btn" type="button" disabled={ !action.complete || action.pending } onClick={ issue }>
                    { bulk ? "Approve " + rows.length + " bars" : "Approve" }
                </button>
                <button className="btn" type="button" onClick={ () => bars.update({}) }>
                    Reload
                </button>
            </div>

            {
                result && (
                    <p className="approved">
                        Approved: { (Array.isArray(result) ? result : [result]).map(r => r?.name).join(", ") }
                    </p>
                )
            }
        </div>
    );
}


/**
 * One bar, with the reasons blaming it while it is selected.
 */
function BarRow({ row, action, path, toggle }: {
    row: Row,
    action: ActionDraft<"approveBar"> | ActionDraft<"approveBars">,
    path: string | null,
    toggle: (id: string) => void
})
{
    return (
        <tr>
            <td>
                <input
                    type="checkbox"
                    aria-label={ "Select " + row.name }
                    checked={ path !== null }
                    onChange={ () => toggle(row.id) }
                />
            </td>
            <td>{ row.name }</td>
            <td>{ row.description }</td>
            <td>{ formatValue(row.approved, "Timestamp") }</td>
            <td>{ path !== null && <Reasons action={ action } path={ path }/> }</td>
        </tr>
    )
}


/**
 * The reasons blaming one selected bar, claimed so that ActionRefusals doesn't show them again.
 */
function Reasons({ action, path }: { action: ActionDraft<any>, path: string })
{
    const refusals = useRefusals(action, path)
    if (!refusals.length)
    {
        return null
    }

    return (
        <ul className="refusals">
            {
                refusals.map((refusal, index) => (
                    <li key={ index } className={ "refusal " + refusal.code.toLowerCase() }>
                        { refusalMessage(refusal) }
                    </li>
                ))
            }
        </ul>
    )
}
