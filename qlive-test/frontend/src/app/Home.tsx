import { useState } from "react";
import {
    DataGrid,
    FilterInput,
    GraphQLResponseError,
    GraphQLTransportError,
    operatorFilter,
    Temporal,
    useFilters,
    useInjection,
    useWorkingSet,
    WorkingSet
} from "@qlivedev/qlive-ts";
import { Q_Foo, Q_FooResult } from "./Q_Foo";

type FooRow = Q_FooResult["rows"][number];

/**
 * The search form's filters. Declared once, outside the view: useFilters() goes by their order, and a filter is a
 * value like any other.
 */
const SEARCH = [
    {field: "description", filter: operatorFilter("containsIgnoreCase")},
    {field: "num", filter: operatorFilter("between", "Int")}
];

export default function Home() {

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
    const [description, num] = search.columns;

    // The rows are edited in a working set, which lives as long as the editing does. The grid registers the
    // document with it and shows its drafts: an edit, a deletion or a new row shows before it is saved, and
    // stays across page turns until it is.
    const [ws] = useState(() => new WorkingSet());
    const { dirty, conflicts, merge, undo } = useWorkingSet(ws);

    return (
        <div className="foo-list">
            <h1>Home</h1>

            <form className="foo-search" onSubmit={ ev => ev.preventDefault() }>
                <label>
                    Description contains
                    <FilterInput column={ description }/>
                </label>
                <label>
                    Num between
                    <FilterInput column={ num }/>
                </label>
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
                    "num",
                    "flag",
                    "owner",
                    {field: "created", nowrap: true},
                    {
                        heading: "",
                        render: row => <FooActions ws={ ws } row={ row }/>
                    }
                ] }
                className="qlive-grid-striped qlive-grid-hover"
            />
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
        <span className="foo-actions">
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
