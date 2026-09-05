import {FilterDSL, inject} from "@quinscape/qlive-ts";
import {Q_Foo, Q_FooResult} from "./Q_Foo";

const {field, value} = FilterDSL;

interface HomeProps
{
    foos: Q_FooResult;
}

export default function Home({foos = inject(Q_Foo)}: HomeProps) {

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
                foos.update({offset: 1, pageSize: 20}).then(result => { console.log(result) });
            }}>
                Test
            </button>
            </div>
        </div>
    );
}
