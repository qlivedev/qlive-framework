import { useState } from "react";
import { formatValue, issueAction, refusalMessage, refusalsOf, useInjection } from "@qlivedev/qlive-ts";
import type { ActionOutcome, Refusal } from "@qlivedev/qlive-ts";
import type { ApproveResult } from "../../types";
import { Q_BarApprove, Q_BarApproveResult } from "./Q_BarApprove";

/**
 * Actions: approving bars through BarLogic's approveBar and approveBars.
 *
 * The rows go to the action as they are -- issueAction() sends each one's id and version, and the server loads
 * the row as it is stored. Nothing here checks a rule: the server does, and a refusal comes back as an outcome
 * with one reason per broken rule, each blaming a field by its path. The view puts each reason next to what it
 * blames.
 *
 * The action writes the approval, which gives each row a new version, so the rows are read again after it:
 * approving one again is then no conflict.
 *
 * Worth trying:
 *  - a bar without a description, which the stored-state rule refuses
 *  - "notify" with an empty comment, which the input rule refuses -- both at once give two reasons
 *  - several bars at once, one of them without a description: none is approved, and the reason names its row
 *  - /bar/edit in another window: change a bar there and approve it here without reloading, which is a conflict
 */
type Row = Q_BarApproveResult["rows"][number]

export default function Approve()
{
    const bars: Q_BarApproveResult = useInjection(Q_BarApprove, { config: { pageSize: 20 } });

    const [selected, setSelected] = useState<string[]>([])
    const [comment, setComment] = useState("")
    const [notify, setNotify] = useState(false)
    // with the ids it was issued for: a refusal counts the rows of its request, not of the current selection
    const [issued, setIssued] = useState<{ ids: string[], outcome: ActionOutcome<ApproveResult[]> } | null>(null)
    const outcome = issued?.outcome

    // the rows in the order they were selected, which is the order the request sends them in
    const rows = selected.map(id => bars.rows.find(row => row.id === id)).filter((row): row is Row => !!row)

    const approve = async () => {
        const ids = rows.map(row => row.id)
        if (rows.length === 1)
        {
            const single = await issueAction("approveBar", { bar: rows[0], comment, notify })
            setIssued({ ids, outcome: single.refused ? single : { refused: false, result: [single.result] } })
        }
        else
        {
            const all = await issueAction("approveBars", { bars: rows, comment })
            setIssued({ ids, outcome: all.refused ? all : { refused: false, result: all.result.filter(r => r !== null) } })
        }
        bars.update({})
    }

    // The single action names its subject "bar", the bulk one "bars", with the row's position after it.
    const blamedOn = (id: string): Refusal[] => {
        const index = issued?.ids.indexOf(id) ?? -1
        if (!outcome?.refused || index < 0)
        {
            return []
        }
        return issued!.ids.length === 1
            ? refusalsOf(outcome.refusals, "bar")
            : refusalsOf(outcome.refusals, "bars." + index)
    }

    const toggle = (id: string) =>
        setSelected(current => current.includes(id) ? current.filter(s => s !== id) : [...current, id])

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
                        bars.rows.map(row => {
                            const index = selected.indexOf(row.id)
                            return (
                                <tr key={ row.id }>
                                    <td>
                                        <input
                                            type="checkbox"
                                            aria-label={ "Select " + row.name }
                                            checked={ index >= 0 }
                                            onChange={ () => toggle(row.id) }
                                        />
                                    </td>
                                    <td>{ row.name }</td>
                                    <td>{ row.description }</td>
                                    <td>{ formatValue(row.approved, "Timestamp") }</td>
                                    <td>
                                        <Reasons refusals={ blamedOn(row.id) }/>
                                    </td>
                                </tr>
                            )
                        })
                    }
                </tbody>
            </table>

            <div className="qlive-form">
                <label>
                    Comment
                    <input type="text" value={ comment } onChange={ ev => setComment(ev.target.value) }/>
                </label>
                <Reasons refusals={ outcome?.refused ? refusalsOf(outcome.refusals, "comment") : [] }/>

                <label>
                    <input
                        type="checkbox"
                        checked={ notify }
                        disabled={ rows.length > 1 }
                        onChange={ ev => setNotify(ev.target.checked) }
                    />
                    Notify the owner (one bar only)
                </label>
            </div>

            <div className="toolbar">
                <button className="btn" type="button" disabled={ !rows.length } onClick={ approve }>
                    { rows.length > 1 ? "Approve " + rows.length + " bars" : "Approve" }
                </button>
                <button className="btn" type="button" onClick={ () => bars.update({}) }>
                    Reload
                </button>
            </div>

            {
                outcome && !outcome.refused && (
                    <p className="approved">
                        Approved: { outcome.result.map(result => result.name).join(", ") }
                    </p>
                )
            }
        </div>
    );
}


/**
 * The reasons blaming one thing, in the page's locale.
 */
function Reasons({ refusals }: { refusals: Refusal[] })
{
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
