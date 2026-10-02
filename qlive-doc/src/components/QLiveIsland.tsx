import {type ComponentType, useEffect, useState} from "react";
import {addFixture, ErrorBoundary, FixtureScope, type QLiveConfig, type QLiveFixture} from "@qlivedev/qlive-ts";
import "@qlivedev/qlive-ts/styles.css";
import {translationsFor} from "../demo/translations";

/*
 * Both globs are lazy: a page fetches the views and fixtures it shows, not every one there is. The fixtures come
 * without config, which all of them share -- see tooling/addFixture.mjs -- and which a page fetches once, adding
 * the English the demos show.
 */
const VIEW_PREFIX = "../../../qlive-test/frontend/src/app/";
const views = import.meta.glob<{default: ComponentType}>("../../../qlive-test/frontend/src/app/**/*.tsx");

const FIXTURE_PREFIX = "../demo/fixtures/";
const fixtures = import.meta.glob<QLiveFixture>("../demo/fixtures/**/*.json", {import: "default"});
const sharedConfig = () => import("../demo/config.json").then(module => {
    const config = module.default as unknown as QLiveConfig;
    config.translations ??= translationsFor(config);
    return config;
});

type Demo = {
    View: ComponentType
    fixture: QLiveFixture
}

async function load(view: string): Promise<Demo>
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

    const [loaded, config] = await Promise.all([loadFixture(), sharedConfig()]);
    const fixture = {...loaded, config: loaded.config ?? config};

    // In the order startup() keeps: QLive initialized, then the view imported, so that code the view runs on import
    // finds the config and its translations. FixtureScope adds the fixture again, which changes nothing.
    await addFixture(fixture);
    const module = await loadView();

    return {View: module.default, fixture};
}

/**
 * What the given fixture holds, for the page: its route and the rows of each document,
 * "grid/filters: Q_FooList with 23 Foo rows". The fixture's description says more -- when it was recorded, where and
 * as whom -- which is for whoever finds the file, not for a reader of the docs.
 */
function captionOf(fixture: QLiveFixture): string
{
    const documents = Object.entries(fixture.data).flatMap(([injectionId, source]) => {
        const document = Object.values(source.data ?? {})[0] as {type?: string, rows?: unknown[]} | null;
        if (!Array.isArray(document?.rows))
        {
            return [];
        }
        const name = injectionId.substring(fixture.route.length + 1);
        return [name + " with " + document.rows.length + " " + (document.type ? document.type + " " : "") + "rows"];
    });
    return fixture.route + (documents.length ? ": " + documents.join(", ") : "");
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
 * Runs a view of qlive-test on its recorded fixture, with what the fixture holds below it. Client-only: a view
 * needs QLive initialized, which its FixtureScope does. Every island on a page adds its fixture to the same QLive,
 * each view reading its injections by its route, so a page can show several demos -- one per view.
 */
export default function QLiveIsland({view}: QLiveIslandProps)
{
    const [demo, setDemo] = useState<Demo | null>(null);
    const [error, setError] = useState<unknown>(null);

    useEffect(
        () => {
            let current = true;
            load(view).then(
                loaded => current && setDemo(loaded),
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

    if (!demo)
    {
        return null;
    }

    const {View, fixture} = demo;

    // The boundary goes outside the scope: a fixture that doesn't fit the page throws while the scope renders. The
    // caption goes outside both, so it still says what the view ran on when the view fails.
    return (
        <>
            <ErrorBoundary>
                <FixtureScope fixture={ fixture }>
                    <View/>
                </FixtureScope>
            </ErrorBoundary>
            <p className="qlive-demo-description">{ captionOf(fixture) }</p>
        </>
    );
}
