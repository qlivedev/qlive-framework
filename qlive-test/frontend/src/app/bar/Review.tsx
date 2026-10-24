import { useState } from "react";
import {
    ActionRefusals,
    Form,
    issueAction,
    refusalMessage,
    Refusal,
    useActionDraft,
    useInjection,
    useWorkingSet,
    WorkingSet
} from "@qlivedev/qlive-ts";
import { Q_BarReview, Q_BarReviewResult } from "./Q_BarReview";

/**
 * Actions on the rows a form edits: each bar is a form of its own over BarLogic's approveBar, its row the action's
 * subject, and a quote through quoteBar, which takes the bar as it is being edited.
 *
 * `<Form row={ draft } action={ approve }/>` derives the layout from both: the row's fields, then the action's
 * input, the comment in the text area main.tsx's widget overrides give it. Save and Approve sit outside the form,
 * one connected to it by the working set, the other by the action draft.
 *
 * Approving and quoting differ in what they take. The bar is approveBar's subject, a stored row: the server loads
 * it and decides on what is saved, so while the form holds changes, Approve stays disabled -- save first. quoteBar
 * takes it as `@Input`, the values being edited, saved or not, and holds the num to its rule in memory. The quote
 * is issued with issueAction() directly: one call, nothing to gather, so no draft is held for it.
 *
 * Approving writes the bar, which gives it a new version. The response says so, and the working set takes it in
 * before the outcome arrives, so approving again, or saving, holds to the version the approval left -- nothing
 * here reads the bars again.
 *
 * Worth trying:
 *  - change the num and quote before saving, then try a num of 0, which the rule refuses
 *  - clear a description and save: Approve then fails at the description field, where the rule blames it
 *  - change a field without saving: Approve is disabled until the save
 *  - approve the same bar twice: no conflict, the working set followed the first approval
 */
export default function Review()
{
    const bars: Q_BarReviewResult = useInjection(Q_BarReview, { config: { pageSize: 3 } });

    const [ws] = useState(() => {
        const set = new WorkingSet()
        set.register(bars)
        return set
    })
    const { dirty, merge, undo } = useWorkingSet(ws, { watch: true })

    return (
        <div className="form-example">
            <h1>Reviewing bars</h1>
            <div className="toolbar">
                <button className="btn" type="button" disabled={ !dirty } onClick={ () => merge() }>
                    Save
                </button>
                <button className="btn" type="button" disabled={ !dirty } onClick={ undo }>
                    Undo
                </button>
            </div>
            {
                bars.rows.map(row => <BarReview key={ row.id } draft={ ws.edit(row) }/>)
            }
        </div>
    );
}


/**
 * One bar: its form, filling approveBar's subject, and the buttons acting on it.
 */
function BarReview({ draft }: { draft: Q_BarReviewResult["rows"][number] })
{
    const approve = useActionDraft("approveBar")
    const [quote, setQuote] = useState<{ price: number } | { refusals: Refusal[] } | null>(null)

    const issueQuote = async () => {
        const outcome = await issueAction("quoteBar", { draft })
        setQuote(outcome.refused ? { refusals: outcome.refusals } : { price: outcome.result })
    }

    return (
        <section className="bar-review">
            <Form row={ draft } action={ approve }/>
            <ActionRefusals action={ approve }/>
            <div className="toolbar">
                <button
                    className="btn"
                    type="button"
                    disabled={ !approve.complete || approve.pending }
                    onClick={ () => approve.issue() }
                >
                    Approve
                </button>
                <button className="btn" type="button" onClick={ issueQuote }>
                    Quote
                </button>
            </div>
            {
                approve.unsaved.length > 0 && <p className="warning">Save the bar before approving it.</p>
            }
            {
                approve.outcome && !approve.outcome.refused && (
                    <p className="approved">Approved: { approve.outcome.result.name }</p>
                )
            }
            {
                quote && ("price" in quote
                    ? <p className="quote">Quote: { quote.price }</p>
                    : (
                        <ul className="refusals">
                            { quote.refusals.map((refusal, index) => <li key={ index }>{ refusalMessage(refusal) }</li>) }
                        </ul>
                    ))
            }
        </section>
    )
}
