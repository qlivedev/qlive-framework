import {ParsedQuery, QuerySelection} from "./parseQuery";
import {QueryConversionMap, SelectionNode} from "../converter";
import {findType, unwrapAll} from "../type-utils";
import {GraphQLType} from "../GraphQLSchema";

/**
 * Container type the top-level selections of an operation are fields of. Subscriptions
 * are not in here: their container type has no established name on the Java side
 * yet, and nothing converts them, so they get an empty map.
 */
const CONTAINER_TYPES: { [operation: string]: string } = {
    query: "QueryType",
    mutation: "MutationType"
}

/**
 * Strips the list and non-null modifiers off a type reference as it was written in a
 * variable definition, e.g. "[Foo!]!" -> "Foo".
 */
function namedTypeOf(written: string): string
{
    return written.replace(/[\[\]!]/g, "").trim()
}

function nodesFor(selections: QuerySelection[], type: GraphQLType): { [key: string]: SelectionNode }
{
    const nodes: { [key: string]: SelectionNode } = {}

    for (let i = 0; i < selections.length; i++)
    {
        const selection = selections[i];

        const field = type.fields?.find(f => f.name === selection.name);
        if (!field)
        {
            throw new Error(`Field "${selection.name}" not found in ${type.name}`)
        }

        const node: SelectionNode = {
            type: unwrapAll(field.type).name
        }

        if (selection.selections.length > 0)
        {
            const fieldType = findType(node.type);
            if (fieldType.kind === "UNION")
            {
                // a union is only ever selected through fragments, which the map has
                // no way of expressing: one result key would need a type per member
                throw new Error(`Cannot build a conversion map for union type ${node.type}`)
            }
            node.fields = nodesFor(selection.selections, fieldType)
        }

        nodes[selection.key] = node
    }
    return nodes
}

/**
 * Builds the conversion map of a parsed query by resolving its selections against the
 * schema: every selection contributes the type of the field it was taken from, under
 * the key its value appears under in the result.
 *
 * Deriving it here rather than having the server prepare one keeps a single
 * implementation, and it is what lets a query put together at runtime convert its
 * result at all -- nobody could have prepared a map for it.
 *
 * @param parsed    parsed query
 *
 * @returns conversion map for it
 */
export function buildConversionMap(parsed: ParsedQuery): QueryConversionMap
{
    if (parsed.usesFragments)
    {
        // the selections we have are missing whatever the fragments contributed, so a
        // map built from them would convert some fields and quietly skip others
        throw new Error(
            "Cannot build a conversion map for " + (parsed.name ?? "an unnamed query") +
            ": fragments are not supported"
        )
    }

    const variables: { [name: string]: string } = {}
    const names = Object.keys(parsed.variables)
    for (let i = 0; i < names.length; i++)
    {
        const name = names[i];
        variables[name] = namedTypeOf(parsed.variables[name])
    }

    const containerType = CONTAINER_TYPES[parsed.operation]

    return {
        selections: containerType ? nodesFor(parsed.selections, findType(containerType)) : {},
        variables
    }
}
