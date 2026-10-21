import * as fs from "node:fs";
import {fileURLToPath} from "node:url";
import {describe, expect, it} from "vitest";
import {analyzeSourceTree} from "../../src/vite/trackUsage";

/**
 * Keeps the tags qlive-ts ships with in step with its source. A new or reworded i18n() call in QLive fails this test
 * until its tag is in the file, with its English: that is written by hand, which is the point of the file.
 */
describe("i18n-en.json", () => {

    const english: Record<string, string> =
        JSON.parse(fs.readFileSync(new URL("../../src/vite/i18n-en.json", import.meta.url), "utf-8"));

    it("holds the tag of every static i18n() call in qlive-ts, and nothing else", () => {

        // QLive imports i18n() from its module, not from the package an application imports it from, so the
        // analysis tracks it under a name of its own. A dynamic tag is a field or section name, which comes into
        // the translations from the domain.
        const analysis = analyzeSourceTree({
            sourceRoot: fileURLToPath(new URL("../../src", import.meta.url)),
            trackedFunctions: {qliveI18n: {module: "./i18n", fn: "", varArgs: true}},
            onSkippedCall: () => {},
        });

        const tags = new Set<string>();
        for (const usage of Object.values(analysis.usages) as {calls?: Record<string, unknown[][]>}[])
        {
            for (const args of usage.calls?.qliveI18n ?? [])
            {
                tags.add(args[0] as string);
            }
        }

        // sorted, so that the file stays sorted too
        expect(Object.keys(english)).toEqual([...tags].sort());
    });

    it("holds an English translation with the tag's placeholders", () => {
        const placeholders = (text: string) => [...text.matchAll(/\{\d+\}/g)].map(m => m[0]).sort();

        for (const [tag, text] of Object.entries(english))
        {
            expect(text.trim(), tag).not.toBe("");
            expect(placeholders(text), tag).toEqual(placeholders(tag));
        }
    });
});
