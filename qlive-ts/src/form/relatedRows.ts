/*
 * relatedRows: the rows a picker offers, from the server's related-rows endpoint.
 */
import config, {isFixture} from "../config";
import {getConverter} from "../converter";
import {toJSON} from "../FilterDSL";
import type {QueryConfigDelta} from "../QueryDocument";
import {objectFields, unwrapAll} from "../type-utils";

/**
 * What relatedRows() resolves with.
 */
export interface RelatedRows<R = any>
{
    /** the type the rows are of */
    type: string
    /** the rows of the page, with the key fields foreign keys reference and the type's name fields */
    rows: R[]
    /** how many rows the condition finds, the page aside */
    rowCount: number
}

/** where the server serves related rows, below the context path */
const RELATED_ROWS_URI = "/api/related-rows"

/** rows seen per type, by key field and key, for naming a related row a form's row wasn't read with */
const seen = new Map<string, Map<string, any>>()

function seenKey(field: string, key: unknown): string
{
    return field + "\u0000" + String(key)
}

function remember(type: string, rows: any[])
{
    let byKey = seen.get(type)
    if (!byKey)
    {
        byKey = new Map()
        seen.set(type, byKey)
    }
    for (const row of rows)
    {
        for (const field of Object.keys(row))
        {
            byKey.set(seenKey(field, row[field]), row)
        }
    }
}

/**
 * A row of the type relatedRows() has seen with the given value in the given field, or null.
 *
 * @internal
 */
export function seenRow(type: string, field: string, key: unknown): any
{
    return seen.get(type)?.get(seenKey(field, key)) ?? null
}

/**
 * Converts a row from its wire format, field by field along the type.
 */
function convertRow(type: string, row: Record<string, unknown>): Record<string, unknown>
{
    const fields = objectFields(type)
    const converted: Record<string, unknown> = {}
    for (const [name, value] of Object.entries(row))
    {
        const field = fields.find(f => f.name === name)
        const fieldType = field ? unwrapAll(field.type).name! : null
        const converter = fieldType ? getConverter(fieldType) : null
        converted[name] = value === null || value === undefined || !converter
            ? value
            : converter.fromServer(value, fieldType!)
    }
    return converted
}

/**
 * The rows of a type a foreign key references, as the given config narrows and pages them: what a picker offers.
 *
 * The server answers for the targets of foreign keys only, with the fields they reference and the type's name
 * fields, and the config filters and sorts by those only. The type's own query config applies under the one given,
 * and the page is held to the type's maximum page size.
 *
 * Running on a fixture, there is no server to ask, and this rejects without trying.
 *
 * @param type      the related type, e.g. "AppUser"
 * @param delta     the config, over the type's own and the defaults
 */
export async function relatedRows<R = any>(type: string, delta: QueryConfigDelta = {}): Promise<RelatedRows<R>>
{
    if (isFixture())
    {
        throw new Error(
            "QLive runs on a fixture, which serves no related rows: rows of " + type + " were asked for."
        )
    }

    const {contextPath, csrfToken} = config()
    const response = await fetch(
        window.location.origin + contextPath + RELATED_ROWS_URI,
        {
            method: "POST",
            credentials: "same-origin",
            headers: {
                "Content-Type": "application/json",
                "Accept": "application/json",
                [csrfToken!.header]: csrfToken!.value
            },
            body: JSON.stringify({
                type,
                config: delta.condition !== undefined ? {...delta, condition: toJSON(delta.condition)} : delta
            })
        }
    )
    const answer = await response.json()
    if (response.status !== 200)
    {
        throw new Error(answer?.error ?? "The server answered " + response.status + " to a related rows request.")
    }

    const rows = (answer.rows as Record<string, unknown>[]).map(row => convertRow(type, row)) as R[]
    remember(type, rows)
    return {type, rows, rowCount: answer.rowCount}
}
