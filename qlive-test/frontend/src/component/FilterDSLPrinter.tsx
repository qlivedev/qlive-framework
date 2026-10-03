import React from "react"
import {decompileFilter, i18n, QueryConfig, ResolvedColumn, unawaited, useInjection} from "@qlivedev/qlive-ts";
import {Q_FooList, Q_FooListResult} from "../app/grid/Q_FooList";
import {CNode, FieldExpression, FilterExpression, matchSort, matchSortPart} from "@qlivedev/qlive-ts/filter";


function sortOrder(expr: CNode)
{
    if (expr.type === "Operation" && expr.name === "desc")
    {
        return i18n("descending")
    }

    return i18n("ascending");
}

function unwrapSortOrder(expr: CNode)
{
    if (expr.type === "Operation" && expr.name === "desc")
        return expr.operands[0]
    if (expr.type === "Operation" && expr.name === "asc")
        return expr.operands[0]
    return expr;
}

export function sortExpression(expr: FieldExpression)
{
    if (typeof expr === "string")
    {
        if (expr.charAt(0) === "!")
        {
            return expr.substring(1) + ", " + i18n("descending")
        }
        else
        {
            return expr + ", " + i18n("ascending")
        }
    }
    else
    {
        return decompileFilter(unwrapSortOrder(expr)) + ", " + sortOrder(expr);
    }
}

export type FilterDSLPrinterProps = {
    value: CNode | FieldExpression[] | null;
}

const FilterDSLPrinter = ({ value } : FilterDSLPrinterProps) => {

    if (Array.isArray(value)) {
        return (
            <>
                {
                    !!value.length && (
                        <ol>
                            {
                                value.map((expr,idx) => (
                                    <li key={idx}>
                                        {
                                            sortExpression(expr)
                                        }
                                    </li>
                                ))
                            }
                        </ol>
                    )
                }

            </>
        );
    }
    else
    {
        return (
            <>
                {
                    value ? <textarea cols={60} rows={ 16    } readOnly={true} value={ decompileFilter(value) }/>  : "<none>"
                }
            </>
        );
    }
};

export default FilterDSLPrinter;