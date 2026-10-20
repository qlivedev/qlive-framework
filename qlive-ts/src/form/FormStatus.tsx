/*
 * FormStatus: the live region announcing what happened to a form's row that the user didn't do.
 */
import {JSX, useLayoutEffect} from "react";

import {Announcement, useFormState} from "./FormContext";

/**
 * The region itself, as the form renders it by default and FormStatus where it is placed.
 *
 * @internal
 */
export function StatusRegion({announcement}: { announcement: Announcement }): JSX.Element
{
    // a new element per announcement, so the same words twice are announced twice
    return (
        <div className="qlive-form-status" role="status">
            <span key={ announcement.count }>{ announcement.text }</span>
        </div>
    )
}

/**
 * Where the form's live region goes: what a screen reader is told when somebody else changed a field on screen, or a
 * save left a conflict -- once per such event, not once per field. What the user changes is not announced.
 *
 * Every form has the region, at its top unless a FormStatus inside it places it elsewhere, at any depth. A layout
 * places it with a `{status: true}` node. The region is there from the start and empty while there is nothing to say,
 * since a screen reader only announces changes to a region it already knows. It is visually hidden by default; its
 * class is `qlive-form-status`.
 *
 * One FormStatus per form: a second one warns in development and announces everything twice.
 *
 * @throws Error outside a Form
 */
export function FormStatus(): JSX.Element
{
    const form = useFormState()
    const {registerStatus} = form

    useLayoutEffect(() => registerStatus(), [registerStatus])

    return <StatusRegion announcement={ form.announcement }/>
}
