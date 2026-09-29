import {condition, conditionsEqual, FilterExpression, operation, value} from "../FilterDSL";
import {ColumnFilter} from "./filters";

/**
 * A filter for the digits of a number: `numberContainsFilter()` on an order number finds 12345 for "234". The term
 * is `contains` on the field as text, so it works for any scalar with a text form.
 */
export function numberContainsFilter(): ColumnFilter<string[]>
{
    return {
        arity: 1,

        toCondition(target, [text])
        {
            const digits = text.trim();
            return digits === ""
                ? null
                : condition("contains", [operation("toString", [target]), value(digits)]) as FilterExpression;
        },

        fromCondition(target, term)
        {
            if (term.type !== "Condition" || term.name !== "contains" || term.operands.length !== 2)
            {
                return null;
            }
            const [digits, text] = term.operands;
            return digits.type === "Operation" && digits.name === "toString" && digits.operands.length === 1 &&
                conditionsEqual(digits.operands[0], target) &&
                text.type === "Value" && typeof text.value === "string"
                ? [text.value]
                : null;
        }
    };
}
