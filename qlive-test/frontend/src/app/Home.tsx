import {FilterDSL, inject} from "@quinscape/qlive-ts";
import {Q_Foo, Q_FooResult} from "./Q_Foo";

const {field, value} = FilterDSL;

interface HomeProps
{
    foos: Q_FooResult;
}

export default function Home({foos = inject(Q_Foo, {})}: HomeProps) {
    const filter =
        field("name").eq(value("Foo #1")).or(field("owner.login").eq(value("admin")))

    return (
        <div>
            <h1>Home</h1>

            <dl>
                <dt>Foo</dt>
                <dd>
                    <pre>
                        {
                            JSON.stringify({foos}, null, 4)
                        }
                    </pre>
                </dd>
                <dt>Filter</dt>
                <dd>
                    <pre>
                    {
                        JSON.stringify(filter, null, 4)
                    }
                    </pre>
                </dd>


            </dl>
            <pre>
            </pre>
        </div>
    );
}
