import {useLayoutEffect, useRef, useState} from "react";

import {evaluateQuery} from "./evaluate";
import {unawaited} from "./grid/unawaited";
import {QueryConfig, QueryDocument, QueryDocumentSnapshot, setLocalSource} from "./QueryDocument";
import useQueryDocument from "./useQueryDocument";

const DEFAULT_CONFIG: QueryConfig = {condition: null, offset: 0, pageSize: 0, sortFields: []};

/**
 * A query document over rows the browser holds rather than a query: its update() filters, sorts and pages them.
 *
 *     const doc = localDocument("Foo", rows, {pageSize: 10, sortFields: ["name"]});
 *
 * Everything that takes a query document takes this one -- a DataGrid, its filters, sort headers and pager -- and
 * can't tell the difference. The condition is evaluated as conditionPredicate() has it, the order is
 * sortComparator()'s. The rows are of a type of the schema, which is what field paths are checked against; they are
 * held as given, so rows added to the array show with the next update().
 *
 * In a view, useLocalDocument() is this with the subscription that makes an update() show.
 *
 * @param type      GraphQL type name of the rows
 * @param rows      all the rows, not a page of them
 * @param config    initial config. By default no condition, no order and all rows on one page.
 *
 * @returns query document
 */
export function localDocument<T extends object>(type: string, rows: T[], config: Partial<QueryConfig> = {}): QueryDocument<T>
{
    return localDocumentOf(type, () => rows, config);
}

/**
 * localDocument() over rows read at every update().
 */
function localDocumentOf<T extends object>(type: string, rows: () => readonly T[], config: Partial<QueryConfig>): QueryDocument<T>
{
    const initial: QueryConfig = {...DEFAULT_CONFIG, ...config};
    const source = (config: QueryConfig) => evaluateQuery(type, rows(), config);
    const first = source(initial);
    const document = new QueryDocument<T>(type, initial, first.rows, first.rowCount);
    setLocalSource(document, source);
    return document;
}

/**
 * A query document over rows the view holds, see localDocument(), and the current snapshot of it.
 *
 *     const foos = useLocalDocument("Foo", rows, {pageSize: 10});
 *
 *     <DataGrid doc={ foos } columns={ ["name", "num"] }/>
 *
 * The config is the initial one; after that it is the document's, changed through update() like any other. New rows
 * -- an array that isn't the one before -- are filtered, sorted and paged under the config the document has.
 *
 * Rules of hooks apply: call it at the top level of a view, unconditionally.
 *
 * @param type      GraphQL type name of the rows
 * @param rows      all the rows, not a page of them
 * @param config    initial config. By default no condition, no order and all rows on one page.
 *
 * @returns snapshot of the document
 */
export function useLocalDocument<T extends object>(
    type: string,
    rows: T[],
    config: Partial<QueryConfig> = {}
): QueryDocumentSnapshot<T>
{
    const current = useRef(rows);
    current.current = rows;

    const [document] = useState(() => localDocumentOf(type, () => current.current, config));

    // the rows the document shows, which are the rows before the ones just given until the update below
    const shown = useRef(rows);
    useLayoutEffect(
        () => {
            if (shown.current !== rows)
            {
                shown.current = rows;
                unawaited(document.update({}));
            }
        },
        [document, rows]
    );

    return useQueryDocument(document) as unknown as QueryDocumentSnapshot<T>;
}
