import {CNode, isComputedValue, RawValue} from "../FilterDSL"

function convert(value: RawValue, scalarType: string)
{
    if (scalarType === "Timestamp" || scalarType === "Date")
    {
        return "DateTime.fromISO(" + JSON.stringify(value) + ")"
    }

    return JSON.stringify(value)
}

function indent(level: number): string
{
    let s = ""
    if (level <= 0)
    {
        return s
    }
    for (let i = 0; i < level; i++)
    {
        s += "    "
    }
    return s;
}

const topLevelConditions = {
    or: true,
    and: true,
    not: true
}

const simplifiedValues = new Set([
    "Boolean",
    "String",
    "Int",
    "Timestamp"
])


/**
 * Converts the given FilterDSL condition graph into a pretty-formatted source
 * form using the FilterDSL API (and DateTime.fromIso() expressions)
 *
 * @param {Object} condition        condition graph
 * @param {Number} level            starting indentation level
 * @param {Object} match            Optional object to mark. The given node is assumed to an instance in the graph and
 *                                  the source output of that object is marked is enclosed in \/*>>*\/ \/*<<*\/ to mark the object
 * @param {boolean} invert          inverts condition output (mostly internal usage)
 *
 * @return {string} pretty-printed source string. If match was used, the >> << might prevent it from being valid JavaScript
 */
export function decompileFilter(condition: CNode | null, level: number = 0, match: CNode | null = null, invert: boolean = true): string
{
    if (!condition)
    {
        return indent(level) + "null";
    }

    const {type} = condition;
    const markerL = match === condition ? "/*>>*/ " : "";
    const markerR = match === condition ? " /*<<*/" : "";

    if (type === "Value")
    {
        const value = condition.value;

        if (isComputedValue(value))
        {
            if (value.name === "now")
            {
                return indent(level) + markerL + "now()" + markerR
            } else if (value.name === "today")
            {
                return indent(level) + markerL + "today()" + markerR
            }
        }
    }


    const nextLevel = level >= 0 ? level + 1 : level


    if (type === "Field")
    {
        return indent(level) + markerL + "field(" + JSON.stringify(condition.name) + ")" + markerR;
    } else if (type === "Condition" || type === "Operation")
    {
        const {name, operands} = condition;
        const isMethod = invert && !topLevelConditions.hasOwnProperty(name)
        const args = isMethod ? operands.slice(1) : operands
        // an empty argument list stays on one line: "isTrue()", not "isTrue(\n)"
        const multiLine = level >= 0 && args.length > 0
        const argSource = args.map(o => decompileFilter(o, nextLevel, match, invert))
            .join(multiLine ? ",\n" : ",")
        const call = markerL + name + (multiLine ? "(\n" + argSource + "\n" + indent(level) + ")" : "(" + argSource + ")") + markerR

        if (isMethod)
        {
            return decompileFilter(operands[0], level, match, true) + "." + call
        }
        return indent(level) + call;
    } else if (type === "Value")
    {
        const {value, scalarType} = condition;
        if (value !== null && simplifiedValues.has(scalarType))
        {
            return indent(level) + markerL + "value(" + convert(value, scalarType) + ")" + markerR;
        }
        return indent(level) + markerL + "value(" + JSON.stringify(value) + ", " + JSON.stringify(
            scalarType) + ")" + markerR;

    } else if (type === "Values")
    {
        const {values, scalarType} = condition;
        return indent(level) + markerL + "values(" + JSON.stringify(scalarType) + ", " + JSON.stringify(
            values) + ")" + markerR;

    } else if (type === "Component")
    {
        const {id, condition: component} = condition;
        return indent(level) + markerL + "component(" + JSON.stringify(
            id) + ", " + (level >= 0 ? "\n" : "") + decompileFilter(component, nextLevel, match,
            true) + (level >= 0 ? "\n" : "") + indent(level) + ")" + markerR;
    } else
    {
        throw new Error("Unhandled type: " + type);
    }
}
