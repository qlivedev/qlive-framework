import * as fs from "node:fs";
import {fileURLToPath} from "node:url";

/**
 * The module id QLive's own calls are filed under in the analysis an application's build hands the backend. Not a
 * module of the application, so it matches no route, and the backend reads the i18n() tags out of it like out of any
 * other module.
 */
export const FRAMEWORK_MODULE = "@qlivedev/qlive-ts";

/**
 * The tags of QLive's own i18n() calls, sorted. An application's build has no qlive-ts source to analyze, so they
 * ship next to the plugin. test/vite/frameworkTags.test.ts keeps the file in step with the source: `vitest -u`
 * rewrites it.
 */
const FRAMEWORK_TAGS = new URL("./i18n-tags.json", import.meta.url);

/**
 * The analysis entry for QLive's own i18n() calls, for the backend to offer their translations along with the
 * application's.
 */
export function frameworkUsages(): Record<string, unknown>
{
    if (!fs.existsSync(FRAMEWORK_TAGS))
    {
        throw new Error(
            `[track-usage] ${fileURLToPath(FRAMEWORK_TAGS)} is missing: it ships with qlive-ts, and without it ` +
            `QLive's own texts can't be translated.`
        );
    }

    const tags: string[] = JSON.parse(fs.readFileSync(FRAMEWORK_TAGS, "utf-8"));

    return {
        [FRAMEWORK_MODULE]: {
            requires: [],
            calls: {i18n: tags.map(tag => [tag])},
        },
    };
}
