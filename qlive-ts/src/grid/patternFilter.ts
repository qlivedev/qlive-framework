import {and, CNode, condition, field, FilterExpression, not, operation, or, value} from "../FilterDSL";
import {ColumnFilter} from "./filters";

/** characters with a meaning in a regular expression, the server's as much as JavaScript's */
const SPECIAL = /[.[\]()*+?{}^$|\\]/g;
const ESCAPED = /\\([.[\]()*+?{}^$|\\])/g;

/**
 * The expression a pattern matches against: the field, as text for a type that isn't.
 */
function subject(path: string, scalarType: string): CNode
{
    return scalarType === "String" ? field(path) : operation("toString", [field(path)]);
}

/**
 * The term of one word of a pattern: `containsIgnoreCase` for a plain word, a regular expression over the whole
 * lowercased value for one with wildcards.
 */
function wordTerm(path: string, scalarType: string, word: string): FilterExpression
{
    const negated = word.startsWith("!");
    const text = (negated ? word.substring(1) : word).trim();
    const term = text.includes("*")
        ? condition("likeRegex", [
            operation("lower", [subject(path, scalarType)]),
            value("^" + text.toLowerCase().split(/\*+/).map(part => part.replace(SPECIAL, "\\$&")).join(".*") + "$")
        ])
        : condition("containsIgnoreCase", [subject(path, scalarType), value(text)]);
    return (negated ? not(term) : term) as FilterExpression;
}

/**
 * Whether a node is the subject of a pattern on the path.
 */
function isSubject(node: CNode, path: string, scalarType: string): boolean
{
    if (scalarType === "String")
    {
        return node.type === "Field" && node.name === path;
    }
    return node.type === "Operation" && node.name === "toString" && node.operands.length === 1 &&
        node.operands[0].type === "Field" && node.operands[0].name === path;
}

/**
 * The word a term of one was written from, `null` if it isn't one.
 */
function wordOf(node: CNode, path: string, scalarType: string): string | null
{
    if (node.type !== "Condition")
    {
        return null;
    }
    if (node.name === "not" && node.operands.length === 1)
    {
        const word = wordOf(node.operands[0], path, scalarType);
        return word !== null && !word.startsWith("!") ? "!" + word : null;
    }
    if (node.operands.length !== 2 || node.operands[1].type !== "Value" || typeof node.operands[1].value !== "string")
    {
        return null;
    }
    const [target, {value: text}] = node.operands as [CNode, { value: string }];
    if (node.name === "containsIgnoreCase")
    {
        return isSubject(target, path, scalarType) && text !== "" && !/[*&|]/.test(text) && !text.startsWith("!")
            ? text
            : null;
    }
    if (node.name === "likeRegex" && target.type === "Operation" && target.name === "lower" &&
        target.operands.length === 1 && isSubject(target.operands[0], path, scalarType))
    {
        const match = /^\^(.*)\$$/.exec(text);
        if (!match)
        {
            return null;
        }
        const parts = match[1].split(".*");
        if (parts.length < 2 || parts.some(part => part.replace(ESCAPED, "").match(SPECIAL)))
        {
            return null;
        }
        return parts.map(part => part.replace(ESCAPED, "$1")).join("*");
    }
    return null;
}

/**
 * The operands of a logical condition, or the node itself as the only one.
 */
function operandsOf(node: CNode, name: "and" | "or"): CNode[]
{
    return node.type === "Condition" && node.name === name ? node.operands : [node];
}

/**
 * A filter for a search pattern:
 *
 * - `*` stands for any text, and a word with it matches the whole value: `Foo*` starts with "Foo", `*#1` ends with
 *   "#1". A word without it matches anywhere in the value.
 * - `&` between words: all of them match. `|` between groups of those: one of the groups matches. `&` binds
 *   tighter, and there are no brackets.
 * - `!` before a word: the word does not match.
 *
 * Case doesn't matter. Empty words are left out, so a pattern being typed filters by the words already there:
 * `foo &` filters for "foo".
 *
 * A plain word becomes `containsIgnoreCase`, a word with wildcards a regular expression (`likeRegex`) on the
 * lowercased value, and the words of a group are combined with `and()`, the groups with `or()`. A field that isn't
 * a String is matched as text.
 *
 * @param scalarType    scalar type of the field. Default "String".
 */
export function patternFilter(scalarType: string = "String"): ColumnFilter<string[]>
{
    return {
        arity: 1,

        toCondition(path, [pattern])
        {
            const groups = pattern.split("|").map(group =>
                and(...group.split("&")
                    .map(word => word.trim())
                    .filter(word => word !== "" && word !== "!")
                    .map(word => wordTerm(path, scalarType, word)))
            );
            return or(...groups);
        },

        fromCondition(path, term)
        {
            const groups: string[] = [];
            for (const group of operandsOf(term, "or"))
            {
                const words: string[] = [];
                for (const node of operandsOf(group, "and"))
                {
                    const word = wordOf(node, path, scalarType);
                    if (word === null)
                    {
                        return null;
                    }
                    words.push(word);
                }
                groups.push(words.join(" & "));
            }
            return [groups.join(" | ")];
        }
    };
}
