import { DataGrid, useInjection, useLocalDocument } from "@qlivedev/qlive-ts";
import { Q_OwnerCatalog } from "../Q_OwnerCatalog";

/**
 * A grid over rows the view already holds.
 *
 * The injection loads every user at once. A local document over them filters, sorts and pages in the browser, with
 * the same conditions a query would send to the server, so the grid can't tell the difference and neither can the
 * user -- except that nothing goes over the wire.
 */
export default function Local()
{
    const owners = useInjection(Q_OwnerCatalog, {config: {pageSize: 0}});

    const ownerList = useLocalDocument("AppUser", owners.rows, {pageSize: 2, sortFields: ["login"]});

    return (
        <div className="grid-example">
            <h1>Local rows</h1>
            <DataGrid doc={ ownerList } columns={ ["login"] } pageSizes={ [2, 5, 0] }/>
        </div>
    );
}
