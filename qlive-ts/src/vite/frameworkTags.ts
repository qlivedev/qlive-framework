import * as fs from "node:fs";
import {fileURLToPath} from "node:url";

/**
 * The module id QLive's own calls are filed under in the analysis an application's build hands the backend. Not a
 * module of the application, so it matches no route, and the backend reads the i18n() tags out of it like out of any
 * other module.
 */
export const FRAMEWORK_MODULE = "@qlivedev/qlive-ts";

/**
 * QLive's own i18n() tags with their English, sorted by tag. An application's build has no qlive-ts source to analyze,
 * so the tags ship next to the plugin, and the English with them, so that an application offering English needs no
 * rows for QLive's texts. test/vite/frameworkTags.test.ts keeps the tags in step with the source; the English is
 * written by hand.
 */
const FRAMEWORK_TRANSLATIONS = new URL("./i18n-en.json", import.meta.url);

/**
 * The analysis entry for QLive's own i18n() calls, for the backend to offer their translations along with the
 * application's, and their English as the translation where the application stores none.
 */
export function frameworkUsages(): Record<string, unknown>
{
    if (!fs.existsSync(FRAMEWORK_TRANSLATIONS))
    {
        throw new Error(
            `[track-usage] ${fileURLToPath(FRAMEWORK_TRANSLATIONS)} is missing: it ships with qlive-ts, and without ` +
            `it QLive's own texts can't be translated.`
        );
    }

    const english: Record<string, string> = JSON.parse(fs.readFileSync(FRAMEWORK_TRANSLATIONS, "utf-8"));

    return {
        [FRAMEWORK_MODULE]: {
            requires: [],
            calls: {i18n: Object.keys(english).map(tag => [tag])},
            translations: {en: english},
        },
    };
}
