import {FilterDSL, useInjection} from "@quinscape/qlive-ts";
import {Q_Foo} from "./Q_Foo";

const {field, value} = FilterDSL;

export default function Home() {

    // The view subscribes to the injected document here -- update() below changes it and
    // this re-renders with the new snapshot.
    const foos = useInjection(Q_Foo);

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
                foos.update({offset: 1}).then(result => { console.log(result) });
            }}>
                Test
            </button>
            </div>
        </div>
    );
}
