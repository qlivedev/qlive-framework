import config from "../config";
import {CNode, FilterExpression} from "../FilterDSL";
import {conditionPredicate} from "../evaluate";

/**
 * A rule on a type: a condition that holds for every write of the type's rows, whoever writes.
 *
 * Written server-side, by `@Rules` on a POJO's class or by QLive's RulesMetadataProvider, and delivered in the
 * type meta data as config().meta.types[name].meta.rules. The merge checks the same rules before it writes; the
 * working set checks them first, so that a form shows what the server would refuse.
 *
 * Its paths are the type's own fields, `numB`, and it reads one row and nothing else.
 */
export type Invariant = {
    /**
     * The condition that has to hold
     */
    condition: FilterExpression
    /**
     * The field the rule blames, null where it blames none
     */
    field: string | null
    /**
     * i18n tag of the message
     */
    messageKey: string
    /**
     * Class a layout can react to, null where the rule names none
     */
    errorClass: string | null
}

/**
 * The invariants of the given type, in the order they were named; empty where it has none or is no type.
 *
 * @param typeName      GraphQL type name, known or not
 */
export function invariants(typeName: string): readonly Invariant[]
{
    return config().meta.types[typeName]?.meta?.rules ?? []
}

/** what an invariant reads and its test, compiled once per rule */
const compiled = new WeakMap<Invariant, { fields: Set<string>, test: (row: object) => boolean }>()

function compile(typeName: string, invariant: Invariant)
{
    let found = compiled.get(invariant)
    if (!found)
    {
        const fields = new Set<string>()
        collectFields(invariant.condition, fields)
        found = {fields, test: conditionPredicate(typeName, invariant.condition)}
        compiled.set(invariant, found)
    }
    return found
}

/**
 * The fields an invariant reads.
 *
 * @param typeName      GraphQL type name of the rows
 * @param invariant     the invariant
 */
export function invariantFields(typeName: string, invariant: Invariant): ReadonlySet<string>
{
    return compile(typeName, invariant).fields
}

/**
 * Whether an invariant holds for a row. As in SQL, a comparison with null holds for nothing, so a field a new row
 * hasn't been given breaks a rule reading it.
 *
 * @param typeName      GraphQL type name of the rows
 * @param invariant     the invariant
 * @param row           the row's values, every field the invariant reads among them
 */
export function invariantHolds(typeName: string, invariant: Invariant, row: object): boolean
{
    return compile(typeName, invariant).test(row)
}

function collectFields(node: CNode | null | undefined, fields: Set<string>): void
{
    if (!node)
    {
        return
    }
    switch (node.type)
    {
        case "FieldReference":
            fields.add(node.name)
            break
        case "Condition":
        case "Operation":
            node.operands.forEach(operand => collectFields(operand, fields))
            break
        case "Component":
            collectFields(node.condition, fields)
            break
    }
}
