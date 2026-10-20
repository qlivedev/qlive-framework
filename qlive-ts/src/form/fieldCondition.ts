/*
 * Field conditions: which related rows a to-one field offers, written against the form's row.
 */
import {catalogRows, isCatalog} from "../catalog";
import config from "../config";
import {getConverter} from "../converter";
import {conditionPredicate} from "../evaluate";
import {CNode, field as fieldRef, FilterExpression, value} from "../FilterDSL";
import {isListType, objectFields, unwrapAll} from "../type-utils";
import type {FieldState} from "./useField";

/**
 * The relation behind a to-one field of the type, with its single key field on either side.
 */
function toOneRelation(type: string, name: string): { sourceField: string, targetField: string, targetType: string } | null
{
    const relation = config().meta.relations.find(r => r.sourceType === type && r.leftSideObjectName === name)
    if (!relation || relation.sourceFields.length !== 1)
    {
        return null
    }
    return {sourceField: relation.sourceFields[0], targetField: relation.targetFields[0], targetType: relation.targetType}
}

/**
 * A related row the form's row was read with, but whose key has changed since: it knows its key and nothing else.
 */
function staleRow(type: string, relationName: string, targetField: string, key: unknown): object
{
    const known: Record<string, unknown> = {[targetField]: key}
    return new Proxy(known, {
        has: () => true,
        get: (target, name) => {
            if (typeof name !== "string" || name in target)
            {
                return (target as any)[name]
            }
            throw new Error(
                "A field condition reads " + relationName + "." + name + " of a " + type + ", but " + relationName +
                " was changed since the row was read, and only its key is known. Read the key instead, " +
                relationName + "." + targetField + "."
            )
        }
    })
}

/**
 * The row a field condition is tested on: a copy of the form's row with the candidate in the relation's place.
 *
 * Another relation's object is the row it was read with as long as its key is unchanged. A changed key is named by
 * its catalog row where the target is a catalog type; otherwise only the key is known, and reading another field of
 * it is an error rather than a wrong answer.
 *
 * @param field         the to-one field
 * @param row           the form's row
 * @param candidate     the related row in question, or null
 *
 * @internal
 */
export function conditionRoot(field: FieldState, row: object, candidate: unknown): Record<string, unknown>
{
    const draft = row as Record<string, unknown>
    const root: Record<string, unknown> = {}
    for (const f of objectFields(field.domainType))
    {
        if (f.name === field.name)
        {
            root[f.name] = candidate
            continue
        }
        if (!(f.name in draft))
        {
            continue
        }
        const relation = unwrapAll(f.type).kind === "OBJECT" && !isListType(f.type)
            ? toOneRelation(field.domainType, f.name)
            : null
        const related = draft[f.name] as Record<string, unknown> | null
        if (!relation || !(relation.sourceField in draft))
        {
            root[f.name] = related
            continue
        }
        const key = draft[relation.sourceField]
        if (key === null || key === undefined)
        {
            root[f.name] = null
        }
        else if (related && related[relation.targetField] === key)
        {
            root[f.name] = related
        }
        else if (isCatalog(relation.targetType))
        {
            root[f.name] = catalogRows(relation.targetType).find(r => r[relation.targetField] === key) ?? null
        }
        else
        {
            root[f.name] = staleRow(field.domainType, f.name, relation.targetField, key)
        }
    }
    return root
}

/**
 * Tells the candidates a field's condition lets through, testing each in the form's row (see conditionRoot()). In
 * the browser, so for rows the client has: a catalog type's.
 *
 * @param field     the to-one field, with a condition
 * @param row       the form's row
 *
 * @internal
 */
export function candidatePredicate(field: FieldState, row: object): (candidate: unknown) => boolean
{
    const test = conditionPredicate(field.domainType, field.condition)
    return candidate => test(conditionRoot(field, row, candidate))
}

/** the scalar type a path of the type ends in */
function pathType(type: string, path: string): string
{
    let owner = type
    const segments = path.split(".")
    for (let i = 0; i < segments.length; i++)
    {
        const found = objectFields(owner).find(f => f.name === segments[i])
        if (!found)
        {
            throw new Error(owner + " has no field " + JSON.stringify(segments[i]) + ".")
        }
        if (isListType(found.type))
        {
            throw new Error(
                "A field condition can't read " + JSON.stringify(path) + " of the form's row for the server: it " +
                "leads through a list, which is no one value."
            )
        }
        owner = unwrapAll(found.type).name!
    }
    return owner
}

/** the value at a path of the root, null where something on the way is */
function readRoot(root: Record<string, unknown>, path: string): unknown
{
    let current: unknown = root
    for (const name of path.split("."))
    {
        if (current === null || current === undefined)
        {
            return null
        }
        current = (current as any)[name]
    }
    return current ?? null
}

/**
 * A field's condition as the related-rows endpoint takes it, rooted at the related type: a reference through the
 * relation, `owner.login`, becomes the candidate's own field, `login`, and every other reference becomes the value
 * it has in the form's row, a constant.
 *
 * The result changes when a value the condition reads from the form's row does, which is when the rows are asked
 * for again.
 *
 * @param field     the to-one field, with a condition
 * @param row       the form's row
 *
 * @throws Error for a condition reading the relation object itself, or a list of the form's row
 *
 * @internal
 */
export function endpointCondition(field: FieldState, row: object): FilterExpression | null
{
    if (!field.condition)
    {
        return null
    }
    const prefix = field.name + "."
    const root = conditionRoot(field, row, null)

    function rewrite(node: CNode): CNode
    {
        switch (node.type)
        {
            case "FieldReference":
                if (node.name.startsWith(prefix))
                {
                    return fieldRef(node.name.substring(prefix.length))
                }
                if (node.name === field.name)
                {
                    throw new Error(
                        "A field condition of " + field.domainType + "." + field.name + " reads the relation " +
                        "itself; name a field of it, " + prefix + "<field>."
                    )
                }
            {
                const type = pathType(field.domainType, node.name)
                const live = readRoot(root, node.name)
                const toServer = getConverter(type)?.toServer
                return value((live === null || !toServer ? live : toServer(live, type)) as any, type)
            }
            case "Condition":
            case "Operation":
                return {...node, operands: node.operands.map(rewrite)}
            case "Component":
                return {...node, condition: node.condition && rewrite(node.condition)}
            default:
                return node
        }
    }

    return rewrite(field.condition) as FilterExpression
}
