import {FieldExpression, matchSort, SortDirection, toggleSort} from "../FilterDSL";
import {QueryConfig, QueryConfigDelta} from "../QueryDocument";

/**
 * What useSort() needs of a query document: its config and update(). A snapshot from useInjection() or
 * useQueryDocument() is one as long as its query selects `config`.
 */
export interface SortableDocument
{
    config: QueryConfig;
    update(delta: QueryConfigDelta): Promise<unknown>;
}

/**
 * Where one sort key stands in a document's sort order, and the way to sort by it.
 */
export interface SortState
{
    /**
     * Direction the document sorts by the key in, `null` if its sort order doesn't name the key.
     */
    direction: SortDirection | null;

    /**
     * Index of the key in the sort order, 0 for the most significant field, `null` if the order doesn't name the
     * key. Above 0 only where something other than a header click set a sort of several fields.
     */
    position: number | null;

    /**
     * Sorts the document by the key alone: ascending, or descending if the document sorts by the key ascending and
     * by nothing else. Goes back to the first page, since the rows on any other page are different ones now.
     *
     * @returns the update's promise, settled when the document holds the new rows
     */
    toggle(): Promise<void>;
}

/**
 * The sort state of one sort key -- a column header's, or any other control's that sorts by one thing.
 *
 * Reads the document's config and writes it through update(), and holds nothing of its own: whatever set the sort,
 * this shows it.
 *
 *     const sort = useSort(foos, "name")
 *
 *     <button onClick={ sort.toggle }>
 *         Name { sort.direction === "asc" ? "▲" : sort.direction === "desc" ? "▼" : "" }
 *     </button>
 *
 * The key is a field path (`"owner.name"`) or a FilterDSL expression node. A key naming a direction (`"!name"`,
 * `desc(...)`) is a mistake: the direction is what toggle() changes.
 *
 * @param doc   query document snapshot
 * @param key   what to sort by
 */
export function useSort(doc: SortableDocument, key: FieldExpression): SortState
{
    const match = matchSort(doc.config.sortFields, key);

    return {
        direction: match?.direction ?? null,
        position: match?.index ?? null,
        toggle: () => doc.update({sortFields: toggleSort(doc.config.sortFields, key), offset: 0}).then(() => {})
    };
}
