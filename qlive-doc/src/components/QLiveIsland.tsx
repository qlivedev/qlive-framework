import {type ComponentType, useEffect, useState} from "react";
import {ErrorBoundary, initFixture, type QLiveFixture} from "@qlivedev/qlive-ts";
import "@qlivedev/qlive-ts/styles.css";

/*
 * Both globs are lazy: a page fetches the one view and the one fixture it shows, not every one there is.
 */
const VIEW_PREFIX = "../../../qlive-test/frontend/src/app/";
const views = import.meta.glob<{default: ComponentType}>("../../../qlive-test/frontend/src/app/**/*.tsx");

const FIXTURE_PREFIX = "../demo/fixtures/";
const fixtures = import.meta.glob<QLiveFixture>("../demo/fixtures/**/*.json", {import: "default"});

/**
 * The view whose fixture QLive was initialized with. QLive's config and injections are module state, shared by every
 * island on a page, so a page runs one fixture -- and with it one view.
 */
let active: string | null = null;

async function load(view: string): Promise<ComponentType>
{
    const loadView = views[VIEW_PREFIX + view + ".tsx"];
    const loadFixture = fixtures[FIXTURE_PREFIX + view + ".json"];
    if (!loadView)
    {
        throw new Error("No view " + view + " in qlive-test/frontend/src/app");
    }
    if (!loadFixture)
    {
        throw new Error("No fixture for " + view + " in qlive-doc/src/demo/fixtures");
    }

    if (active !== view)
    {
        if (active)
        {
            throw new Error("This page already runs " + active + ": one demo per page, since QLive runs one fixture at a time");
        }
        active = view;
        await initFixture(await loadFixture());
    }

    return (await loadView()).default;
}

export interface QLiveIslandProps
{
    /**
     * The view, as its path below qlive-test/frontend/src/app without extension, e.g. "grid/Sorting". Its fixture is
     * the file of the same path below src/demo/fixtures.
     */
    view: string
}

/**
 * Runs a view of qlive-test on its recorded fixture. Client-only: a view needs QLive initialized, which happens here.
 */
export default function QLiveIsland({view}: QLiveIslandProps)
{
    const [View, setView] = useState<ComponentType | null>(null);
    const [error, setError] = useState<unknown>(null);

    useEffect(
        () => {
            let current = true;
            load(view).then(
                component => current && setView(() => component),
                e => current && setError(e)
            );
            return () => {
                current = false;
            };
        },
        [view]
    );

    if (error)
    {
        return <p className="qlive-error">{ error instanceof Error ? error.message : String(error) }</p>;
    }

    return View && (
        <ErrorBoundary>
            <View/>
        </ErrorBoundary>
    );
}
