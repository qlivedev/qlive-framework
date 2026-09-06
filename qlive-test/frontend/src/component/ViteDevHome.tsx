import React, {useLayoutEffect, useState} from "react"
import { i18n, config, isListType, isNonNull, unwrapAll, DomainTables } from "@quinscape/qlive-ts"
import type {
    DomainQLMeta,
    GraphQLField, GraphQLInterfaceType, GraphQLObjectType,
    GraphQLSchema,
    GraphQLType,
    GraphQLTypeRef
} from "@quinscape/qlive-ts"
import {posix} from "node:path";



const ViteDevHome = ({}) => {

    const [filter,setFilter] = useState("")

    return (
        <>
            <h1>Vite Dev Root</h1>
            <p>
                {
                    i18n("ViteDevHome Message")
                }
            </p>
            <p>
                <a href="/login">Login</a>
            </p>
            <h2>Domain</h2>
            <details>
                <summary> Details ...</summary>
                <DomainTables
                    filter={ filter }
                    setFilter={ setFilter }
                />
            </details>
        </>
    )
}

export default ViteDevHome
