import React, {useLayoutEffect, useState} from "react"
import { i18n, config, isListType, isNonNull, unwrapAll, DomainTables, Logout, QuickLogin } from "@qlivedev/qlive-ts"
import type {
    DomainQLMeta,
    GraphQLField, GraphQLInterfaceType, GraphQLObjectType,
    GraphQLSchema,
    GraphQLType,
    GraphQLTypeRef,
    QuickLoginUser
} from "@qlivedev/qlive-ts"
import {posix} from "node:path";



/**
 * Returns the field of the given type the quick search matches against.
 *
 * Reads both halves of the application's meta data extension -- the "quickSearchTypes" addendum and the
 * "quickSearch" field meta data property -- without a cast, because src/qlive-meta.d.ts declares them to
 * TypeScript. See ExampleMetadataProvider for the server side writing them.
 */
function quickSearchField(typeName: string) : string | undefined
{
    const fields = config().meta.types[typeName]?.fields

    return fields && Object.keys(fields).find(fieldName => fields[fieldName].quickSearch)
}


export type ViteDevHomeProps = {
    quickLoginUsers: QuickLoginUser[]
}

const ViteDevHome = ({ quickLoginUsers }: ViteDevHomeProps) => {

    const [filter,setFilter] = useState("")

    return (
        <>
            <h1>Vite Dev Root</h1>
            <QuickLogin users={ quickLoginUsers }/>
            <Logout/>
            <ul className="nav-list">
                <li>
                    <a href="/app/home">Home</a>
                </li>
                <li>
                    <a href="/app/bar/live">Bar Live</a>
                </li>
                <li>
                    <a href="/app/bar/edit">Bar Edit</a>
                </li>
            </ul>
            <h2>Domain</h2>
            <details>
                <summary> Details ...</summary>
                <DomainTables
                    filter={ filter }
                    setFilter={ setFilter }
                />
            </details>
            <hr/>
            <h2>Quick Search (metadata example)</h2>
            <ul>
                {
                    config().meta.quickSearchTypes.map(
                        typeName => (
                            <li key={ typeName }>
                                { typeName + "." + quickSearchField(typeName) }
                            </li>
                        )
                    )
                }
            </ul>
        </>
    )
}

export default ViteDevHome
