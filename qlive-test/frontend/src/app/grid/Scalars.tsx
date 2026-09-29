import { DataGrid, useInjection } from "@qlivedev/qlive-ts";
import { Q_QuxList, Q_QuxListResult } from "./Q_QuxList";

/**
 * One column per scalar type, each as the grid shows it without being told anything: the formatting of its
 * converter, and the default filter of its type where there is one. The types without a default filter have an
 * empty filter cell -- a column that should filter them names a filter itself.
 */
export default function Scalars()
{
    const quxes: Q_QuxListResult = useInjection(Q_QuxList, {config: {pageSize: 10}});

    return (
        <div className="grid-example">
            <h1>Scalar types</h1>
            <DataGrid
                doc={ quxes }
                columns={ [
                    "name",
                    "bool",
                    "byteValue",
                    "intValue",
                    "longValue",
                    "doubleValue",
                    "bigDecimalValue",
                    "currencyValue",
                    "stringValue",
                    "dateValue",
                    {field: "timestampValue", nowrap: true}
                ] }
            />
        </div>
    );
}
