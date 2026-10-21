import {fileURLToPath} from "node:url";
import {describe, expect, it} from "vitest";
import {analyzeSourceTree} from "../../src/vite/trackUsage";

/**
 * Keeps the tags qlive-ts ships with in step with its source. A new or reworded i18n() call in QLive fails this test
 * until `vitest -u` writes it into the file, so the change is part of the diff.
 */
describe("i18n-tags.json", () => {

    it("holds the tag of every static i18n() call in qlive-ts", async () => {

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

        await expect(JSON.stringify([...tags].sort(), null, 2) + "\n")
            .toMatchFileSnapshot("../../src/vite/i18n-tags.json");
    });
});
