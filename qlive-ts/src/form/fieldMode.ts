/*
 * Field modes: how a form renders a field, and the mode a field has where nothing says otherwise.
 */
import config from "../config";
import {VERSION} from "../merge/meta";
import {primaryKeyFields} from "../type-utils";

/**
 * How a form renders one field.
 *
 * - `normal` -- an input the user edits
 * - `disabled` -- a disabled input: not focusable, and dimmed
 * - `readOnly` -- a read-only input where the widget has one: focusable and selectable, but not editable
 * - `text` -- the value as text in a span, lined up with the inputs around it
 *
 * `readOnly` and `text` both show a value the user can't change. `readOnly` keeps the look of an input, `text` reads
 * as what it is, which is why it is what a field no form edits gets.
 */
export type FieldMode = "normal" | "disabled" | "readOnly" | "text"

/**
 * The mode a field of a type has where nothing says otherwise: `text` for a field no form edits, `normal` for every
 * other.
 *
 * No form edits
 *
 * - a field of the primary key, which identifies the row rather than describing it,
 * - a computed field, which nothing stores,
 * - the version field, which only the merge writes,
 * - a field whose meta data says `editable: false`.
 *
 * A foreign key is edited, through the picker of the relation it is the key of.
 *
 * @param type      GraphQL type name
 * @param field     name of a field of it
 */
export function defaultFieldMode(type: string, field: string): FieldMode
{
    const meta = config().meta.types[type]?.fields?.[field]
    const edited = field !== VERSION &&
        !primaryKeyFields(type).includes(field) &&
        !meta?.computed &&
        meta?.editable !== false

    return edited ? "normal" : "text"
}
