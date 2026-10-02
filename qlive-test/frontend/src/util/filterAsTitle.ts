import {QueryConfig, ResolvedColumn} from "@qlivedev/qlive-ts";
import {matchSort, matchSortPart} from "@qlivedev/qlive-ts/filter";
import {sortExpression} from "../component/FilterDSLPrinter";

/**
 * Helper function to have each column's title be a description of its sort filter. The return of this function must
 * goes into the headerTitle prop of the datagrid component.
 *
 * @param queryConfig
 */
export default function filterAsTitle(queryConfig : QueryConfig)
{
    const { sortFields } = queryConfig
    return (column: ResolvedColumn)=>
    {
        const match = column.sort && (matchSort(sortFields, column.sort) ?? matchSortPart(sortFields, column.sort));
        return match ? sortExpression(sortFields[match.index]) : undefined;
    }
}