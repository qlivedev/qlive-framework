/*
 * ActionRefusals: the refusals of an action no field shows.
 */
import type {JSX} from "react";

import type {ActionDraft} from "../useActionDraft";
import {Refusals} from "./Field";

/**
 * The refusals of an action's last issue that no part of the view shows: those blaming no field -- a conflict of a
 * subject, a rule over two subjects -- and those blaming a field nothing on screen claims (ActionDraft.claim()). A
 * Field claims its own, and useRefusals() claims for a component of the application's.
 *
 * One per action, wherever the view wants them, inside a form or not; a `qlive-refusals` list, or nothing while there
 * is none.
 *
 *     <ActionRefusals action={ approve }/>
 *
 * @param action    the action draft
 */
export function ActionRefusals({action}: { action: ActionDraft<any> }): JSX.Element | null
{
    return <Refusals refusals={ action.unclaimed }/>
}
