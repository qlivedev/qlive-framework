import {FilterDSL, useInjection} from "@quinscape/qlive-ts";
import {Q_Foo, Q_FooResult} from "./Q_Foo";

const {field, value} = FilterDSL;

export default function Home() {

    // The view subscribes to the injected document here -- update() below changes it and
    // this re-renders with the new snapshot.
    //
    // The parameters are the query's GraphQL variables, and they have to be written out like this:
    // the server runs the query before the page is sent, reading this very call out of the build's
    // static analysis, so anything it cannot see at build time is not there when the query runs.
    const foos : Q_FooResult = useInjection(Q_Foo, {config: {pageSize: 5}});

    // const filter =
    //     field("name").eq(value("Foo #1")).or(field("owner.login").eq(value("admin")))


    return (
        <div>
            <h1>Home</h1>

            <pre>
            {
                JSON.stringify(foos, null, 4)
            }
            </pre>
            <div className="toolbar">

            <button type="button" onClick={ () => {
                foos.update({offset: 1})
            }}>
                Test
            </button>
            </div>
        </div>
    );
}
