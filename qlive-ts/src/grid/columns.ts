/*
 * Grid columns: what a column is given as, and the complete column derived from that and the schema.
 */
import {isValidElement, ReactNode} from "react";

import config from "../config";
import {formatValue} from "../converter";
import {FieldExpression} from "../FilterDSL";
import type {GenericScalar} from "../GraphQL";
import i18n from "../i18n";
import type {Temporal} from "../temporal";
import {isListType, objectFields, unwrapAll} from "../type-utils";
import {booleanFilter} from "./booleanFilter";
import {ColumnFilter, FilterColumn, operatorFilter} from "./filters";

/** values a field path ends at rather than going into */
type Leaf = string | number | boolean | bigint | GenericScalar |
    Temporal.Instant | Temporal.PlainDate | Temporal.PlainDateTime | Temporal.PlainTime | Temporal.ZonedDateTime;

/** one less than the index, to count the depth down */
type Less = [never, 0, 1, 2, 3, 4];

type PathOf<K extends string, V, D extends number> =
    0 extends (1 & V) ? K :
    V extends readonly unknown[] ? never :
    V extends Leaf ? K :
    V extends object ? K | `${ K }.${ FieldPath<V, Less[D]> }` :
    K;

/**
 * The field paths of a row type: its fields, and the fields of the to-one relations it holds, as `"owner.login"`,
 * down to a depth of 4. Lists drop out, since a column shows one value per row.
 *
 * Taken from the result type of the grid's query, a path is only valid if the query selects it, so a typo and a
 * field the query doesn't select are both compile errors.
 *
 * @typeParam T     row type
 */
export type FieldPath<T, D extends number = 4> =
    [D] extends [never] ? never :
    T extends object ? { [K in keyof T & string]-?: PathOf<K, NonNullable<T[K]>, D> }[keyof T & string] :
    never;

/**
 * A column where the defaults derived from its field path won't do. Everything but the field is optional and
 * overrides what is derived.
 *
 * @typeParam R     row type
 */
export interface DataGridColumn<R = any>
{
    /**
     * The field path. Without it the column is an action or computed column and needs `render`.
     */
    field?: FieldPath<R>;

    /**
     * The heading, in place of the i18n() of `Type.field`.
     */
    heading?: ReactNode;

    /**
     * The cell content. An element is used as it is, any other value is formatted like a value of the column's
     * field.
     */
    render?(row: R): unknown;

    /**
     * What the column sorts by, a field path or a FilterDSL expression without a direction, in place of the field;
     * `false` for a column that doesn't sort.
     */
    sort?: FieldExpression | false;

    /**
     * The column's filter in place of the one derived from the field's type; `false` for a column without one.
     */
    filter?: ColumnFilter<any> | false;

    /**
     * Added to the classes of the column's cells, fixed or per row.
     */
    className?: string | ((row: R) => string | undefined);

    /**
     * Keeps the cell content on one line.
     */
    nowrap?: boolean;

    /** CSS width the column doesn't get narrower than */
    minWidth?: string;

    /** CSS width the column doesn't get wider than */
    maxWidth?: string;
}

/**
 * A column as a grid takes it: a field path, which means `{field: path}`, or the object form.
 */
export type GridColumn<R = any> = FieldPath<R> | DataGridColumn<R>;

/**
 * A column with everything derived that can be: what DataGrid renders, and what a hand-written table built on the
 * same hooks gets.
 */
export interface ResolvedColumn<R = any>
{
    /** the field path, `null` for a computed column */
    field: string | null;

    heading: ReactNode;

    /** the cell content of a row */
    render(row: R): ReactNode;

    /** what the column sorts by, `null` if it doesn't sort */
    sort: FieldExpression | null;

    /**
     * The column's filter and the field it filters, `null` if it has none. For a relation column that is its first
     * name field, `"owner.login"`, not the relation.
     */
    filter: FilterColumn | null;

    /** classes of the column's cells for a row */
    className(row: R): string | undefined;

    /**
     * The field of the row whose working set status the cells show: the field itself for a field of the row, the
     * foreign key for a relation of it. `null` for a field of a related row and for a computed column.
     */
    statusField: string | null;

    nowrap: boolean;
    minWidth?: string;
    maxWidth?: string;
}

/** what a field path ends at */
type Target =
    { kind: "scalar", owner: string, name: string, scalarType: string } |
    { kind: "relation", owner: string, name: string, targetType: string, sourceFields: string[] };

function columnError(path: string, message: string)
{
    return new Error("Column " + JSON.stringify(path) + ": " + message);
}

/**
 * Follows a field path through the schema from the row type.
 */
function resolvePath(type: string, path: string): Target
{
    const segments = path.split(".");
    let owner = type;
    for (let i = 0; i < segments.length; i++)
    {
        const name = segments[i];
        const field = objectFields(owner).find(f => f.name === name);
        if (!field)
        {
            throw columnError(path, owner + " has no field " + JSON.stringify(name) + ".");
        }
        if (isListType(field.type))
        {
            throw columnError(path, name + " of " + owner + " is a list, and a column shows one value per row.");
        }
        const named = unwrapAll(field.type);
        const last = i === segments.length - 1;
        if (named.kind !== "OBJECT")
        {
            if (!last)
            {
                throw columnError(path, name + " of " + owner + " is a " + named.name + ", with no fields below it.");
            }
            return {kind: "scalar", owner, name, scalarType: named.name!};
        }
        if (last)
        {
            const relation = config().meta.relations.find(r => r.sourceType === owner && r.leftSideObjectName === name);
            if (!relation)
            {
                throw columnError(path, name + " of " + owner + " is neither a scalar nor a to-one relation.");
            }
            return {kind: "relation", owner, name, targetType: named.name!, sourceFields: relation.sourceFields};
        }
        owner = named.name!;
    }
    throw columnError(path, "empty path.");
}

/**
 * The value at a field path of a row, `null` where a relation on the way is. A key the row doesn't have is an
 * error: the query doesn't select it.
 *
 * @param row       row, or the related row a name field is read from
 * @param path      path in it
 * @param column    the column's field, for the error
 * @param selected  the path as the query has to select it, for the error
 */
function valueAt(row: any, path: string, column: string, selected: string = path): unknown
{
    let current = row;
    for (const name of path.split("."))
    {
        if (current === null || current === undefined)
        {
            return null;
        }
        if (!(name in current))
        {
            throw columnError(column, "the rows have no " + JSON.stringify(selected) + ". Select it in the grid's query.");
        }
        current = current[name];
    }
    return current;
}

const NUMBER_SCALARS = new Set(["Int", "Short", "Byte", "Float"]);

/**
 * The filter a column of a scalar type gets unless it names one, `null` for types without one yet.
 */
function defaultFilter(scalarType: string): ColumnFilter<any> | null
{
    if (scalarType === "String")
    {
        return operatorFilter("containsIgnoreCase");
    }
    if (scalarType === "Boolean")
    {
        return booleanFilter();
    }
    if (NUMBER_SCALARS.has(scalarType))
    {
        return operatorFilter("eq", scalarType);
    }
    return null;
}

/**
 * Completes a grid column: the heading, the display, the sort key and the filter, derived from the field path and
 * the schema where the column doesn't state them.
 *
 * - A **scalar path** shows the value formatted for its type (see `formatValue()`), sorts by the field, and filters
 *   by type: `containsIgnoreCase` for String, a yes/no select for Boolean, `eq` for Int, Short, Byte and Float.
 *   Other types have no default filter.
 * - A **to-one relation path** (`"owner"`) stands for the related row, named by the target type's `nameFields`
 *   meta: it shows the name fields, most significant first, and sorts and filters by the first one. The query has
 *   to select them (`owner { id login }`).
 *
 * The heading is the i18n() of the owning type and the field, `"Foo.name"`, `"AppUser.login"`, so a type's fields
 * are labeled once for every grid showing them.
 *
 * A path the schema doesn't have, a list, a non-scalar that isn't a to-one relation, or a relation whose type has
 * no name fields where the column needs them is an error naming the column.
 *
 * @param type      row type of the grid's document
 * @param column    the column as given
 */
export function resolveColumn<R = any>(type: string, column: NoInfer<GridColumn<R>>): ResolvedColumn<R>
{
    const given: DataGridColumn<R> = typeof column === "string" ? {field: column} : column;
    const path: string | null = given.field ?? null;

    const cellClass = given.className;
    const resolved: ResolvedColumn<R> = {
        field: path,
        heading: given.heading ?? "",
        render: () => null,
        sort: given.sort || null,
        filter: null,
        className: typeof cellClass === "function" ? cellClass : () => cellClass,
        statusField: null,
        nowrap: given.nowrap ?? false,
        minWidth: given.minWidth,
        maxWidth: given.maxWidth
    };

    if (path === null)
    {
        if (!given.render)
        {
            throw new Error("A column without a field needs a render function.");
        }
        if (given.filter)
        {
            throw new Error("A column without a field has nothing to filter; give it a field or drop the filter.");
        }
        const render = given.render;
        resolved.render = row => display(render(row), "");
        return resolved;
    }

    const target = resolvePath(type, path);
    if (!path.includes("."))
    {
        resolved.statusField = target.kind === "scalar"
            ? path
            : target.sourceFields.length === 1 ? target.sourceFields[0] : null;
    }
    if (given.heading === undefined)
    {
        resolved.heading = i18n(target.owner + "." + target.name);
    }

    let scalarType = "";
    let defaultSort: string;
    let filterField: string;
    let filterType: string;

    if (target.kind === "scalar")
    {
        scalarType = target.scalarType;
        defaultSort = filterField = path;
        filterType = scalarType;
        resolved.render = row => formatValue(valueAt(row, path, path), scalarType);
    }
    else
    {
        const nameFields = config().meta.types[target.targetType]?.meta?.nameFields ?? [];
        const needsNames = !given.render || given.sort === undefined || given.filter === undefined;
        if (needsNames && !nameFields.length)
        {
            throw columnError(
                path,
                target.targetType + " has no name fields to show, sort and filter the relation by. Configure them " +
                "on the server, or give the column render, sort and filter."
            );
        }
        const names = nameFields.map(nameField => ({
            path: nameField,
            scalarType: nameFieldType(target.targetType, nameField, path)
        }));
        defaultSort = filterField = path + "." + nameFields[0];
        filterType = names[0]?.scalarType;
        resolved.render = row => {
            const related = valueAt(row, path, path);
            if (related === null || related === undefined)
            {
                return "";
            }
            return names
                .map(n => formatValue(valueAt(related, n.path, path, path + "." + n.path), n.scalarType))
                .filter(text => text !== "")
                .join(" ");
        };
    }

    if (given.render)
    {
        const render = given.render;
        resolved.render = row => display(render(row), scalarType);
    }

    if (given.sort === undefined)
    {
        resolved.sort = defaultSort;
    }

    if (given.filter)
    {
        resolved.filter = {field: path, filter: given.filter};
    }
    else if (given.filter === undefined)
    {
        const filter = defaultFilter(filterType);
        resolved.filter = filter && {field: filterField, filter};
    }

    return resolved;
}

function nameFieldType(targetType: string, nameField: string, column: string): string
{
    const target = resolvePath(targetType, nameField);
    if (target.kind !== "scalar")
    {
        throw columnError(column, "name field " + JSON.stringify(nameField) + " of " + targetType + " isn't a scalar.");
    }
    return target.scalarType;
}

/**
 * What a render function returned, as cell content: an element as it is, anything else formatted.
 */
function display(value: unknown, scalarType: string): ReactNode
{
    return isValidElement(value) || Array.isArray(value) ? value as ReactNode : formatValue(value, scalarType);
}

/**
 * The key of a row: its primary key, from the `uniqueKeys` meta of the type, or its `id` for a type without one.
 * The values of a key of several fields are joined with commas.
 *
 * @param type      row type
 * @param row       the row
 */
export function rowKey(type: string, row: any): string
{
    const primary = config().meta.types[type]?.meta?.uniqueKeys?.find(k => k.primary);
    const fields = primary ? primary.fields : ["id"];
    return fields.map(f => {
        if (!(f in row))
        {
            throw new Error(
                "The rows of " + type + " have no " + JSON.stringify(f) + ", which is part of its key. Select it in " +
                "the query."
            );
        }
        return String(row[f]);
    }).join(",");
}
