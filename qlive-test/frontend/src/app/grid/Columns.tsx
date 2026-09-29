import { DataGrid, useInjection } from "@qlivedev/qlive-ts";
import { field, value } from "@qlivedev/qlive-ts/filter";
import { Q_FooList, Q_FooListResult } from "./Q_FooList";

/**
 * What a column can say beyond its field. A field path alone is a column; the object form overrides one thing at a
 * time and derives the rest.
 */
export default function Columns()
{
    const foos: Q_FooListResult = useInjection(Q_FooList, {config: {pageSize: 5}});

    return (
        <div className="grid-example">
            <h1>Columns</h1>
            <DataGrid
                doc={ foos }
                columns={ [
                    // a heading of its own; the rest is derived from the field
                    {field: "name", heading: "Foo"},

                    // a path through a to-one relation, shown, sorted and filtered like a field of the row
                    "fooType.name",

                    // classes per row: the value decides how the cell looks
                    {field: "num", className: row => row.num > 1000 ? "grid-example-large" : undefined},

                    // a render function returning a plain value is formatted like the field, here as a Timestamp
                    {field: "created", render: row => row.created, nowrap: true},

                    // long text kept to one line and cut off at a width
                    {field: "description", nowrap: true, maxWidth: "12rem"},

                    // a computed column: no field, so it needs a render function, and its sort is an expression the
                    // server evaluates. It has no filter, because nothing derives one without a field.
                    {
                        heading: "Num / 10",
                        render: row => Math.floor(row.num / 10),
                        sort: field("num").div(value(10))
                    },

                    // a column that neither sorts nor filters
                    {field: "owner", sort: false, filter: false}
                ] }
            />
        </div>
    );
}
