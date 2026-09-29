import { DataGrid, unawaited, useInjection } from "@qlivedev/qlive-ts";
import { field, value } from "@qlivedev/qlive-ts/filter";
import { Q_FooList, Q_FooListResult } from "./Q_FooList";

/**
 * Sort orders a header click can't set, offered by a control of the view's own.
 *
 * A header click always sorts by that column alone. Anything more -- several fields, an expression -- is set on the
 * document from outside, and the headers show it as well as they can: the direction and the position of each field
 * in the order. A column whose field takes part in an expression, like num in its last digit, shows the expression's
 * direction and position in a color of its own.
 */
const ORDERS = [
    {label: "Owner, then name", sortFields: ["owner.login", "name"]},
    {label: "Flagged first, then largest num", sortFields: ["!flag", "!num"]},
    {label: "Last digit of num", sortFields: [field("num").mod(value(10))]},
    {label: "Owner, then last digit of num", sortFields: ["owner.login", field("num").mod(value(10))]}
];

export default function Sorting()
{
    const foos: Q_FooListResult = useInjection(Q_FooList, {config: {pageSize: 5}});

    return (
        <div className="grid-example">
            <h1>Sort orders</h1>
            <p>
                <select
                    value=""
                    onChange={ ev => unawaited(foos.update({sortFields: ORDERS[Number(ev.target.value)].sortFields, offset: 0})) }
                >
                    <option value="" disabled>Sort by …</option>
                    { ORDERS.map((order, i) => <option key={ i } value={ i }>{ order.label }</option>) }
                </select>
            </p>
            <DataGrid doc={ foos } columns={ ["name", "num", "flag", "owner"] }/>
        </div>
    );
}
