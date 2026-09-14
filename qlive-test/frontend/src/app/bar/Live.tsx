import { PubSubConnection, useDocumentWatch, useInjection } from "@quinscape/qlive-ts";
import { useSyncExternalStore } from "react";
import { Q_BarNames, Q_BarNamesResult } from "./Q_BarNames";

/**
 * Rows nobody here is editing, kept honest by push.
 *
 * The other half of the story /bar/edit tells. Nothing on this page is a draft and nothing can be merged,
 * so a change notification is not a conflict -- it is the news that what is on screen is no longer what the
 * database holds. No values travel with one, so this view cannot show the new name; what it can do is say
 * so and offer to read them again, which is `update({})` and nothing more.
 *
 * Worth trying in two windows: open /bar/edit in one and this in the other, change a Bar's name and save.
 * Then do it again with the description, which this query does not select -- nothing happens, because the
 * subscription asked about the fields this view shows and the server never sent the message.
 */
export default function Live()
{
    const bars: Q_BarNamesResult = useInjection(Q_BarNames, { config: { pageSize: 20 } });

    // The snapshot above knows the document it was taken of, so watching the rows on screen is the value
    // beside it and not the query again. What it returns is what somebody else changed under it since it
    // was read.
    const live = useDocumentWatch(bars);

    const connection = useSyncExternalStore(PubSubConnection.subscribe, PubSubConnection.getSnapshot);

    // One entry per row already, however many messages arrived about it, so this is an index and not a
    // deduplication: every row below asks whether it is in it.
    const changed = new Set(live.remoteChanged.map(row => row.id));

    return (
        <div className="bar-live">
            <h1>Bars, live</h1>

            <p className={ "connection " + connection.status }>
                push: { connection.status }
            </p>

            {
                live.stale && (
                    <p className="warning">
                        { changed.size === 1 ? "A row" : changed.size + " rows" } changed while you were
                        looking at { changed.size === 1 ? "it" : "them" }.
                        <button className="btn" type="button" onClick={ () => bars.update({}) }>
                            Reload
                        </button>
                    </p>
                )
            }

            <table className="rows">
                <thead>
                    <tr>
                        <th>id</th>
                        <th>name</th>
                    </tr>
                </thead>
                <tbody>
                    {
                        bars.rows.map(row => (
                            // The mark is per row and not per field: this view has no second value to
                            // show, so "this line is out of date" is the whole of what it knows.
                            <tr key={ row.id } className={ changed.has(row.id) ? "qlive-remote-changed" : "" }>
                                <td>{ row.id }</td>
                                <td>{ row.name }</td>
                            </tr>
                        ))
                    }
                </tbody>
            </table>
        </div>
    );
}
