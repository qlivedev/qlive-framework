import React from "react"
import { i18n, config } from "@quinscape/qlive-ts"

function DomainType(props: { schema: any, type: GraphQL })
{
    return (
        <div className="domain-type">
            <h1>{ type.name }</h1>


        </div>
    );
}

const ViteDevHome = ({}) => {
    const { schema } = config();
    return (
        <>
            <h1>Vite Dev Root</h1>
            <p>
                {
                    i18n("ViteDevHome Message")
                }
            </p>
            <p>
                <a href="home">Home</a>
            </p>
            <details>
                <summary>Domain</summary>
                <div>
                    {
                        schema.types
                            .filter(t => t.kind === "Gra")
                            .map(t => (
                            <DomainType
                                key={ t.name }
                                schema={ schema }
                                type={ t }
                            />
                        ))
                    }
                </div>
            </details>
        </>
    )
}

export default ViteDevHome
