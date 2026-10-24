import { ReactNode, useEffect, useState } from "react";
import {
    ActionDraft,
    ActionRefusals,
    refusalMessage,
    useActionDraft,
    useInjection,
    useRefusals
} from "@qlivedev/qlive-ts";
import { Q_BarApprove, Q_BarApproveResult } from "./Q_BarApprove";
import { Q_BazLink, Q_BazLinkResult } from "./Q_BazLink";

/**
 * An action over two subjects of different types: BarLogic's linkBaz associates a baz with a bar.
 *
 * Nothing connects the two rows yet, so no form fills them. Two parts of the view set the two arguments instead,
 * one list per type, each setting its subject on the same action draft. Each subject has a rule of its own, over
 * its own stored fields, and a refusal blames the subject it is about: the list shows it below the rows.
 *
 * The view holds the ids chosen and sets the rows as they are now, so a list read again -- after a link, which
 * gives the bar a new version -- hands the action the new version.
 *
 * Worth trying:
 *  - a bar or a baz without a description, which its rule refuses -- both at once, and both are reported
 *  - /bar/edit in another window: change the chosen bar there and link it here, which is a conflict at the bar
 */
export default function Link()
{
    const bars: Q_BarApproveResult = useInjection(Q_BarApprove, { config: { pageSize: 10 } });
    const bazes: Q_BazLinkResult = useInjection(Q_BazLink, { config: { pageSize: 10 } });

    const link = useActionDraft("linkBaz")

    const [barId, setBarId] = useState<string | null>(null)
    const [bazId, setBazId] = useState<string | null>(null)
    const bar = bars.rows.find(row => row.id === barId) ?? null
    const baz = bazes.rows.find(row => row.id === bazId) ?? null

    const { set } = link
    useEffect(() => set("bar", bar), [bar, set])
    useEffect(() => set("baz", baz), [baz, set])

    const issue = async () => {
        const outcome = await link.issue()
        if (!outcome.refused)
        {
            bars.update({})
        }
    }

    return (
        <div className="bar-link">
            <h1>Linking a baz to a bar</h1>

            <Choice title="Bar" rows={ bars.rows } chosen={ barId } choose={ setBarId }>
                <Reasons action={ link } path="bar"/>
            </Choice>
            <Choice title="Baz" rows={ bazes.rows } chosen={ bazId } choose={ setBazId }>
                <Reasons action={ link } path="baz"/>
            </Choice>

            <ActionRefusals action={ link }/>

            <div className="toolbar">
                <button className="btn" type="button" disabled={ !link.complete || link.pending } onClick={ issue }>
                    Link
                </button>
            </div>

            {
                link.outcome && !link.outcome.refused && (
                    <p className="approved">Linked { baz?.name } to { bar?.name }</p>
                )
            }
        </div>
    );
}


/**
 * One list of rows of one type, the chosen one being the subject it sets.
 */
function Choice({ title, rows, chosen, choose, children }: {
    title: string,
    rows: { id: string, name: string, description?: string | null }[],
    chosen: string | null,
    choose: (id: string) => void,
    children: ReactNode
})
{
    return (
        <fieldset>
            <legend>{ title }</legend>
            {
                rows.map(row => (
                    <label key={ row.id } className="choice">
                        <input type="radio" name={ title } checked={ row.id === chosen } onChange={ () => choose(row.id) }/>
                        { row.name } <small>{ row.description ?? "(no description)" }</small>
                    </label>
                ))
            }
            { children }
        </fieldset>
    )
}


/**
 * The reasons blaming one subject or a field of it, claimed so that ActionRefusals doesn't show them again.
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
            { refusals.map((refusal, index) => <li key={ index }>{ refusalMessage(refusal) }</li>) }
        </ul>
    )
}
