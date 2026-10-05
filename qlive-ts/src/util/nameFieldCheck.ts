import {ParsedQuery, QuerySelection} from "./parseQuery";
import {findType, unwrapAll} from "../type-utils";
import {GraphQLType} from "../GraphQLSchema";
import config from "../config";

/**
 * One place in a query where a type with name fields is selected without all of them.
 */
interface MissingNameFields
{
    /** result keys leading to the selection, e.g. "xxx.rows.owner" */
    path: string
    /** type selected there */
    type: string
    /** name fields of the type the selection leaves out */
    missing: string[]
}

function collect(
    selections: QuerySelection[],
    type: GraphQLType,
    prefix: string,
    out: MissingNameFields[]
): void
{
    for (let i = 0; i < selections.length; i++)
    {
        const selection = selections[i];
        if (!selection.selections.length)
        {
            continue
        }

        const field = type.fields?.find(f => f.name === selection.name);
        if (!field)
        {
            // the conversion map has already said so
            continue
        }

        const fieldType = findType(unwrapAll(field.type).name);
        if (fieldType.kind !== "OBJECT")
        {
            continue
        }

        const path = prefix + selection.key;
        const nameFields = config().meta.types[fieldType.name]?.meta?.nameFields ?? [];

        // Under its own name: what shows a row reads the name fields by name, so an alias does not count.
        const missing = nameFields.filter(
            (name: string) => !selection.selections.some(s => s.name === name && s.key === name)
        );
        if (missing.length)
        {
            out.push({path, type: fieldType.name, missing})
        }

        collect(selection.selections, fieldType, path + ".", out)
    }
}

/**
 * Warns about every place the given query selects a type without all of its name fields.
 *
 * Name fields are what a grid column of a relation and pick() show for a row, so a query leaving them out
 * serves a view that cannot show its rows -- which those only find out when they render. Saying it when the
 * query is first used names the query instead of the view. A warning, not an error: a query selecting a
 * related row only for its id is a legitimate one.
 *
 * Mutations are not checked. What one selects is what the caller needs back, not something to show.
 *
 * @param parsed    parsed query, which already has a conversion map, i.e. fits the schema
 */
export function warnMissingNameFields(parsed: ParsedQuery): void
{
    if (parsed.operation !== "query")
    {
        return
    }

    const found: MissingNameFields[] = [];
    collect(parsed.selections, findType("QueryType"), "", found);

    if (found.length)
    {
        console.warn(
            "Query " + parsed.name + " selects rows without all of their name fields, which a grid column " +
            "or pick() showing them needs:\n" +
            found.map(f => "    " + f.path + " (" + f.type + "): " + f.missing.join(", ")).join("\n")
        )
    }
}
