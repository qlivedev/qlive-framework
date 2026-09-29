import {
    DataGrid,
    numberContainsFilter,
    operatorFilter,
    patternFilter,
    pick,
    useInjection
} from "@qlivedev/qlive-ts";
import { Q_FooList, Q_FooListResult } from "./Q_FooList";
import { Q_OwnerCatalog } from "../Q_OwnerCatalog";

/**
 * The filters QLive ships, one per column. A filter is a value the view imports and hands to the column; there is no
 * registry to look it up in, so one of the application's own is passed the same way.
 */
export default function Filters()
{
    const foos: Q_FooListResult = useInjection(Q_FooList, {config: {pageSize: 5}});

    // the users to pick an owner from, all of them, since there are few enough
    const owners = useInjection(Q_OwnerCatalog, {config: {pageSize: 0, sortFields: ["login"]}});

    return (
        <div className="grid-example">
            <h1>Filters</h1>
            <DataGrid
                doc={ foos }
                columns={ [
                    // "*#2* | *#1*": the pattern matches the whole name, * is any text, & and | combine, ! negates
                    {field: "name", filter: patternFilter()},

                    // any FilterDSL operator; between takes two inputs
                    {field: "num", filter: operatorFilter("between", "Int")},

                    // the digits anywhere in the number: "2" finds 123
                    {field: "num", heading: "Num contains", filter: numberContainsFilter()},

                    // the default for a Boolean: any, yes or no
                    "flag",

                    // the users of the catalog, filtering the foreign key
                    {field: "owner", filter: pick(owners)},

                    // the default for a Timestamp: whole days in the user's time zone, open at either end
                    {field: "created", nowrap: true}
                ] }
            />
        </div>
    );
}
