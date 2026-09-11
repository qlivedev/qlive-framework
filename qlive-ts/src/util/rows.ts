import {objectFields, unwrapAll} from "../type-utils";

/**
 * One row of a query result, classified against its type.
 *
 * What separates a value from a row below it is the schema, not anything travelling with the data, and a
 * field the query did not select is simply absent -- which is why what a row carries is discovered rather
 * than declared.
 */
export interface RowVisit
{
    /** the row itself */
    row: any

    /** GraphQL name of its type */
    type: string

    /** the fields the row carries that are not rows of their own, by name */
    values: Map<string, unknown>

    /** the fields the row carries that are */
    relations: RowRelation[]
}

/**
 * One object-valued field of a row, and what is under it.
 */
export interface RowRelation
{
    /** field name, e.g. "bazLinks" */
    field: string

    /** GraphQL name of the type the rows under it are */
    type: string

    /** what is under the field, a to-one read as the one row it is */
    rows: any[]

    /**
     * whether the field held an array. Read off the value and not off the schema, because a query is free
     * to select a to-one where the schema declares a list and the value is what a caller has in its hands.
     */
    list: boolean
}

/**
 * Visits every row of a query result and every row below it.
 *
 * Anything the schema calls an object is a row, however deep it sits, so walking a document reaches
 * everything the query selected. A row is visited after the rows below it, and a row without an id is
 * visited like any other -- there is no entity to hang anything on, but the rows below it are still rows,
 * since a query may select an object without selecting its id.
 *
 * @param rows      the rows to walk
 * @param type      GraphQL name of their type
 * @param visit     called once per row
 */
export function walkRows(rows: any[], type: string, visit: (visited: RowVisit) => void): void
{
    for (const row of rows)
    {
        walkRow(row, type, visit)
    }
}


function walkRow(row: any, type: string, visit: (visited: RowVisit) => void): void
{
    if (!row || typeof row !== "object")
    {
        return
    }

    const values = new Map<string, unknown>()
    const relations: RowRelation[] = []

    for (const field of objectFields(type))
    {
        const value = row[field.name]

        if (value === undefined)
        {
            // a field the query did not select
            continue
        }

        const named = unwrapAll(field.type)

        if (named.kind === "OBJECT")
        {
            const list = Array.isArray(value)
            const below = list ? value : [value]

            relations.push({field: field.name, type: named.name!, rows: below, list})

            below.forEach(nested => walkRow(nested, named.name!, visit))
        }
        else
        {
            values.set(field.name, value)
        }
    }

    visit({row, type, values, relations})
}


/**
 * The rows of one type a store is holding, and the fields it has of them.
 *
 * What a subscription to changes of those rows is built from: the ids say which rows are of interest and
 * the fields say which changes to them are, both being what is actually on screen rather than anything the
 * application declared.
 */
export interface HeldRows
{
    type: string

    /** ids of the rows held, a row without one being no row anything can be said about */
    ids: Set<string>

    /** the fields the store has of them, which is what the query selected or the form bound */
    fields: Set<string>
}

/**
 * What a query result holds, by type.
 *
 * Types come out in the order the walk reached them, and the fields of one type are the union over its
 * rows -- a query selects the same fields for every row of a type, but a to-one selected in two places
 * need not have been selected alike in both.
 *
 * @param rows      the rows to walk
 * @param type      GraphQL name of their type
 *
 * @returns one entry per type reached that had rows with ids
 */
export function heldRows(rows: any[], type: string): HeldRows[]
{
    const held = new Map<string, HeldRows>()

    walkRows(rows, type, visited =>
    {
        const id = visited.row.id

        if (typeof id !== "string" || id.length === 0)
        {
            return
        }

        let entry = held.get(visited.type)

        if (!entry)
        {
            entry = {type: visited.type, ids: new Set(), fields: new Set()}
            held.set(visited.type, entry)
        }

        entry.ids.add(id)
        visited.values.forEach((_, name) => entry!.fields.add(name))
    })

    return [...held.values()]
}
