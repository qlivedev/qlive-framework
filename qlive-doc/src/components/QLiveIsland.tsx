import {type ComponentType, useEffect, useState} from "react";
import {ErrorBoundary, FixtureScope, type QLiveConfig, type QLiveFixture} from "@qlivedev/qlive-ts";
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

    const [module, fixture, config] = await Promise.all([loadView(), loadFixture(), sharedConfig()]);

    return {View: module.default, fixture: {...fixture, config: fixture.config ?? config}};
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
 * Runs a view of qlive-test on its recorded fixture. Client-only: a view needs QLive initialized, which its
 * FixtureScope does. Every island on a page adds its fixture to the same QLive, each view reading its injections by
 * its route, so a page can show several demos -- one per view.
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

    // The boundary goes outside the scope: a fixture that doesn't fit the page throws while the scope renders.
    return (
        <ErrorBoundary>
            <FixtureScope fixture={ fixture }>
                <View/>
            </FixtureScope>
        </ErrorBoundary>
    );
}
