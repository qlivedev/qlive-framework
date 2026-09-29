import { useState } from "react";
import {
    DataGrid,
    FilterInput,
    flagSetFilter,
    GraphQLResponseError,
    GraphQLTransportError,
    numberContainsFilter,
    operatorFilter,
    patternFilter,
    pick,
    Temporal,
    useFilters,
    useInjection,
    useLocalDocument,
    useWorkingSet,
    WorkingSet
} from "@qlivedev/qlive-ts";
import { field, value } from "@qlivedev/qlive-ts/filter";
import { Q_Foo, Q_FooResult } from "../Q_Foo";
import { Q_OwnerCatalog } from "../Q_OwnerCatalog";

type FooRow = Q_FooResult["rows"][number];

/**
 * The search form's filters. Declared once, outside the view: useFilters() goes by their order, and a filter is a
 * value like any other.
 */
const SEARCH = [
    // "foo* & !bar | baz": wildcards, and, or and not
    {field: "description", filter: patternFilter()},
    {field: "num", filter: operatorFilter("between", "Int")},
    {
        field: "flag",
        filter: flagSetFilter([
            {name: "flagged", label: "Flagged", term: target => target.isTrue()},
            {name: "large", label: "Num over 100", term: () => field("num").gt(value(100))}
        ])
    }
];

/**
 * The features of the other grid examples in one view: a search form beside the grid's filter row, a catalog
 * filter, a local document, editing, new and deleted rows, watching and errors. Not the place to learn any one of
 * them -- each has an example of its own -- but the place where they have to work together.
 */
export default function Everything() {

    // The view subscribes to the injected document here -- the grid and the search form update it, and this
    // re-renders with the new snapshot.
    //
    // The parameters are the query's GraphQL variables, and they have to be written out like this:
    // the server runs the query before the page is sent, reading this very call out of the build's
    // static analysis, so anything it cannot see at build time is not there when the query runs.
    const foos : Q_FooResult = useInjection(Q_Foo, {config: {pageSize: 5}});

    // A second owner of the document's condition. The grid writes its filter row as the component "grid", this
    // form writes "search", and the condition is both of them joined with and(): each narrows what the other
    // shows, and neither touches the other's part.
    const search = useFilters(foos, "search", SEARCH);
    const [description, num, flags] = search.columns;

    // A catalog for the owner column's filter: all users, few enough to load whole. The grid can't fetch it
    // itself -- only views inject -- so the view hands it over through pick().
    const owners = useInjection(Q_OwnerCatalog, {config: {pageSize: 0, sortFields: ["login"]}});

    // The same catalog as a list of its own. The injection holds every user already, so this document filters,
    // sorts and pages them in the browser instead of asking the server again.
    const ownerList = useLocalDocument("AppUser", owners.rows, {pageSize: 5});

    // The rows are edited in a working set, which lives as long as the editing does. The grid registers the
    // document with it and shows its drafts: an edit, a deletion or a new row shows before it is saved, and
    // stays across page turns until it is.
    const [ws] = useState(() => new WorkingSet());
    const { dirty, conflicts, merge, undo } = useWorkingSet(ws);

    return (
        <div className="grid-example">
            <h1>Everything at once</h1>

            <form className="grid-example-search" onSubmit={ ev => ev.preventDefault() }>
                <label>
                    Description matches
                    <FilterInput column={ description }/>
                </label>
                <label>
                    Num between
                    <FilterInput column={ num }/>
                </label>
                <FilterInput column={ flags }/>
                <button type="button" className="btn" disabled={ !search.active } onClick={ () => search.reset() }>
                    Clear search
                </button>
            </form>

            <div className="toolbar">
                <button className="btn" type="button" disabled={ !dirty } onClick={ () => merge() }>
                    Save
                </button>
                <button className="btn" type="button" disabled={ !dirty } onClick={ undo }>
                    Undo
                </button>
            </div>

            {
                conflicts.length > 0 && (
                    <p className="warning">
                        Somebody saved rows you changed. Your values are the ones standing -- look at the marked
                        rows and save again.
                    </p>
                )
            }

            {
                // A page turn, a sort or a filter that failed. The grid leaves the rows as they were and the
                // document keeps the error until an update succeeds; what to show for it is the view's call.
                foos.error && <ListError error={ foos.error }/>
            }

            {/* watch: other people's writes to the rows on screen mark them as they happen */}
            <DataGrid
                doc={ foos }
                workingSet={ ws }
                watch
                columns={ [
                    {
                        field: "name",
                        // the row is the draft, so the input writes into the working set
                        render: row => <input value={ row.name } onChange={ ev => { row.name = ev.target.value } }/>
                    },
                    // finds 123 for "2"
                    {field: "num", filter: numberContainsFilter()},
                    "flag",
                    // filters ownerId by the user chosen from the catalog
                    {field: "owner", filter: pick(owners)},
                    // a date range, the default for a Timestamp
                    {field: "created", nowrap: true},
                    {
                        heading: "",
                        render: row => <FooActions ws={ ws } row={ row }/>
                    }
                ] }
                className="qlive-grid-striped qlive-grid-hover"
            />

            <h2>Owners</h2>
            <DataGrid doc={ ownerList } columns={ ["login"] } className="qlive-grid-striped"/>
        </div>
    );
}

/**
 * What a failed update of the list says. The server classifies its errors, so an ended session reads
 * differently from a query the server refused, and a server that didn't answer GraphQL at all differently
 * again.
 */
function ListError({ error }: { error: Error })
{
    if (error instanceof GraphQLResponseError && error.hasClassification("UNAUTHENTICATED"))
    {
        return <p className="warning">Your session has ended. Log in again to page, sort and filter.</p>;
    }
    if (error instanceof GraphQLTransportError)
    {
        return <p className="warning">The server can't be reached right now. Try again in a moment.</p>;
    }
    return <p className="warning">Couldn't update the list: { error.message }</p>;
}

/**
 * What can be done to one row. A new row is a copy of an existing one, which is where the fields the table
 * requires and the grid doesn't show come from.
 */
function FooActions({ ws, row }: { ws: WorkingSet, row: FooRow })
{
    return (
        <span className="grid-example-actions">
            <button className="btn" type="button" onClick={ () => ws.create<FooRow>("Foo", {
                name: row.name + " (copy)",
                num: row.num,
                flag: row.flag,
                type: row.type,
                ownerId: row.ownerId,
                created: Temporal.Now.instant()
            }) }>
                Duplicate
            </button>
            <button className="btn" type="button" onClick={ () => ws.delete(row) }>
                Delete
            </button>
        </span>
    );
}
