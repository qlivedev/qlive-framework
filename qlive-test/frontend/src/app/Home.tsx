import {DataGrid, FilterInput, operatorFilter, useFilters, useInjection} from "@qlivedev/qlive-ts";
import {Q_Foo, Q_FooResult} from "./Q_Foo";

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

    return (
        <div>
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

            <DataGrid
                doc={ foos }
                columns={ [
                    "name",
                    "num",
                    "flag",
                    "owner",
                    {field: "created", nowrap: true}
                ] }
                className="qlive-grid-striped qlive-grid-hover"
            />
        </div>
    );
}
