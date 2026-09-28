import {condition, field, FilterExpression, operation, value} from "../FilterDSL";
import {ColumnFilter} from "./filters";

/**
 * A filter for the digits of a number: `numberContainsFilter()` on an order number finds 12345 for "234". The term
 * is `contains` on the field as text, so it works for any scalar with a text form.
 */
export function numberContainsFilter(): ColumnFilter<string[]>
{
    return {
        arity: 1,

        toCondition(path, [text])
        {
            const digits = text.trim();
            return digits === ""
                ? null
                : condition("contains", [operation("toString", [field(path)]), value(digits)]) as FilterExpression;
        },

        fromCondition(path, term)
        {
            if (term.type !== "Condition" || term.name !== "contains" || term.operands.length !== 2)
            {
                return null;
            }
            const [target, text] = term.operands;
            return target.type === "Operation" && target.name === "toString" && target.operands.length === 1 &&
                target.operands[0].type === "Field" && target.operands[0].name === path &&
                text.type === "Value" && typeof text.value === "string"
                ? [text.value]
                : null;
        }
    };
}
