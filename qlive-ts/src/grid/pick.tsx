import type {JSX} from "react";

import config from "../config";
import {formatValue} from "../converter";
import {condition, conditionsEqual, FilterExpression, value} from "../FilterDSL";
import i18n from "../i18n";
import {objectFields, unwrapAll} from "../type-utils";
import {ColumnFilter, ColumnFilterInputProps} from "./filters";

/**
 * What pick() needs of a query document: its type and its rows. Any snapshot is one.
 */
export interface CatalogDocument<R = any>
{
    type: string;
    rows: readonly R[];
}

export interface PickOptions<R = any>
{
    /**
     * The text an option shows for a row. Default: the row's name fields, most significant first, from the
     * `nameFields` meta of its type.
     */
    label?(row: R): string;
}

const NUMBER_SCALARS = new Set(["Int", "Short", "Byte", "Float"]);

/**
 * The scalar type of a field of a type.
 */
function scalarTypeOf(type: string, name: string): string
{
    const found = objectFields(type).find(f => f.name === name);
    if (!found)
    {
        throw new Error("pick(): " + type + " has no field " + JSON.stringify(name) + ".");
    }
    return unwrapAll(found.type).name!;
}

/**
 * The default label of a catalog row: its name fields, formatted, joined with spaces.
 */
function nameLabel(type: string): (row: any) => string
{
    const nameFields = config().meta.types[type]?.meta?.nameFields ?? [];
    if (!nameFields.length)
    {
        throw new Error(
            "pick(): " + type + " has no name fields to label its rows with. Configure them on the server, or give " +
            "pick() a label function."
        );
    }
    const types = nameFields.map(name => scalarTypeOf(type, name));
    return row => nameFields
        .map((name, index) => {
            if (!(name in row))
            {
                throw new Error("pick(): the rows of " + type + " have no " + JSON.stringify(name) + ". Select it in the query.");
            }
            return formatValue(row[name], types[index]);
        })
        .filter(text => text !== "")
        .join(" ");
}

/** a pick() filter, with what its select offers */
interface PickFilter extends ColumnFilter<string[]>
{
    choices(): { key: string, label: string }[];
}

/**
 * The select of a pick() filter: any, and the rows of the catalog. A key the catalog doesn't hold shows as itself.
 */
function PickSelect({label, values, setValues, filter}: ColumnFilterInputProps<string[]>): JSX.Element
{
    const choices = (filter as PickFilter).choices();
    const current = values[0] ?? "";
    const known = current === "" || choices.some(choice => choice.key === current);
    return (
        <select className="qlive-grid-filter-input" aria-label={ i18n("Filter {0}", label) }
                value={ current }
                onChange={ ev => setValues([ev.target.value === "" ? null : ev.target.value]) }>
            <option value="">{ i18n("Any") }</option>
            { !known && <option value={ current }>{ current }</option> }
            { choices.map(choice => <option key={ choice.key } value={ choice.key }>{ choice.label }</option>) }
        </select>
    );
}

/**
 * A filter choosing one row of a catalog: a select of the rows of a query document the view injected, filtering the
 * foreign key that points at them.
 *
 *     const owners = useInjection(Q_OwnerCatalog, {config: {pageSize: 1000}});
 *
 *     <DataGrid doc={ foos } columns={ ["name", {field: "owner", filter: pick(owners)}] }/>
 *
 * On a relation column it filters the foreign key (`"ownerId"`, see `ColumnFilter.key`); in a search form, give it
 * the foreign key as the field. The term is `eq` on that field with the key of the chosen row, which is its primary
 * key from the `uniqueKeys` meta, a single field. A term set from outside shows as the row it names, or as the key
 * itself where the catalog doesn't hold that row.
 *
 * The catalog is the document as it is: all of it for a small catalog, as much as its page holds otherwise. A table
 * too large to load whole needs a picker with its own search.
 *
 * @param doc       the catalog's query document
 * @param options   how an option is labeled
 */
export function pick<R = any>(doc: CatalogDocument<R>, options: PickOptions<R> = {}): ColumnFilter<string[]>
{
    const primary = config().meta.types[doc.type]?.meta?.uniqueKeys?.find(k => k.primary);
    const keyFields = primary ? primary.fields : ["id"];
    if (keyFields.length !== 1)
    {
        throw new Error("pick(): the primary key of " + doc.type + " is several fields, so no single foreign key points at it.");
    }
    const [keyField] = keyFields;
    const keyType = scalarTypeOf(doc.type, keyField);
    const label = options.label ?? nameLabel(doc.type);

    const keyOf = (row: any) => {
        if (!(keyField in row))
        {
            throw new Error("pick(): the rows of " + doc.type + " have no " + JSON.stringify(keyField) + ". Select it in the query.");
        }
        return row[keyField];
    };

    const filter: PickFilter = {
        arity: 1,
        key: true,
        Input: PickSelect,

        choices: () => doc.rows.map(row => ({key: String(keyOf(row)), label: label(row)})),

        toCondition(target, [key])
        {
            const row = doc.rows.find(row => String(keyOf(row)) === key);
            const raw = row ? keyOf(row) : NUMBER_SCALARS.has(keyType) ? Number(key) : key;
            return condition("eq", [target, value(raw, keyType)]) as FilterExpression;
        },

        fromCondition(target, term)
        {
            if (term.type !== "Condition" || term.name !== "eq" || term.operands.length !== 2)
            {
                return null;
            }
            const [filtered, key] = term.operands;
            return conditionsEqual(filtered, target) && key.type === "Value" && key.value !== null &&
                typeof key.value !== "object"
                ? [String(key.value)]
                : null;
        }
    };
    return filter;
}
