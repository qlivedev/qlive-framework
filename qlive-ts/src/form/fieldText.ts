/*
 * fieldText: a field's value as the text a user reads, and the names of related rows.
 */
import {catalogRows, isCatalog} from "../catalog";
import config from "../config";
import {formatValue} from "../converter";
import i18n from "../i18n";
import {findType, objectFields, primaryKeyFields, unwrapAll} from "../type-utils";
import {seenRow} from "./relatedRows";
import type {FieldState} from "./useField";

/**
 * A related row named by its type's name fields, the key where it has none or the row isn't selected.
 *
 * @internal
 */
export function rowName(type: string, row: any, key: unknown): string
{
    const nameFields = config().meta.types[type]?.meta?.nameFields ?? []
    if (!row || !nameFields.length || !nameFields.every(name => name in row))
    {
        return formatValue(key, "String")
    }
    const fields = objectFields(type)
    return nameFields
        .map(name => formatValue(row[name], unwrapAll(fields.find(f => f.name === name)!.type).name!))
        .filter(text => text !== "")
        .join(" ")
}

/**
 * A value of an enum type as a user reads it, translated as `Type.VALUE`.
 *
 * @internal
 */
export function enumLabel(type: string, value: unknown): string
{
    return i18n(type + "." + String(value))
}

/**
 * The field of the related type a to-one relation's foreign key holds.
 *
 * @internal
 */
export function targetKey(field: FieldState): string
{
    const relation = config().meta.relations.find(r => r.sourceType === field.domainType && r.leftSideObjectName === field.name)
    return relation?.targetFields[0] ?? primaryKeyFields(field.fieldType)[0]
}

/**
 * The value of a field as text, what `text` mode shows: the formatted value, an enum value's label, the related
 * row's name for a relation.
 *
 * @param field     the field
 * @param row       the row it is a field of
 * @param value     the value to show, the field's own by default
 */
export function fieldText(field: FieldState, row: object, value: unknown = field.value): string
{
    switch (field.kind)
    {
        case "scalar":
            return value !== null && value !== undefined && findType(field.fieldType).kind === "ENUM"
                ? enumLabel(field.fieldType, value)
                : formatValue(value, field.fieldType)
        case "toOne":
        {
            if (value === null || value === undefined)
            {
                return ""
            }
            // the relation object is what the row was read with, so it names the key only while that is unchanged;
            // a key picked since is named by its catalog row, or by the row the picker fetched
            const related = (row as any)[field.name]
            const key = targetKey(field)
            const named = related && related[key] === value ? related
                : isCatalog(field.fieldType) ? catalogRows(field.fieldType).find(candidate => candidate[key] === value)
                    : seenRow(field.fieldType, key, value)
            return rowName(field.fieldType, named, value)
        }
        case "manyToMany":
        {
            const key = primaryKeyFields(field.fieldType)[0]
            return ((value ?? []) as any[]).map(related => rowName(field.fieldType, related, related[key])).join(", ")
        }
    }
}
