import * as fs from "node:fs";
import * as path from "node:path";
import * as babel from "@babel/core";
import trackUsagePlugin from "../../../src/vite/babel/trackUsagePlugin.js";

/**
 * Runs the track-usage plugin over one fixture against a fixed set of tracked functions
 * covering every matching mode the plugin has.
 *
 * Configured the way `trackUsage.ts` configures it rather than the way the upstream
 * suite did: TypeScript is reached by parser plugin instead of @babel/preset-typescript,
 * so these tests exercise the path QLive actually runs and the preset stays out of the
 * dependencies. Nothing is emitted either way -- the plugin only reads the AST.
 *
 * The modules the fixtures import from -- "./service/lookup" and friends -- do not exist
 * on disk and do not need to: babel parses, it never resolves.
 */
export function transform(relPath: string, typeScript = false, indexes = false): void
{
    const servicePath = typeScript ? "./typescript/service/" : "./service/";

    const opts: babel.InputOptions = {
        plugins: [
            [
                trackUsagePlugin,
                {
                    trackedFunctions: {
                        moduleFn: {module: servicePath + "moduleFn", fn: "", varArgs: true},
                        nonVar: {module: servicePath + "nonVarMod", fn: ""},
                        lookup: {module: servicePath + "lookup", fn: "thing", varArgs: true},
                        nvLookup: {module: servicePath + "lookup", fn: "nonvar"},
                        multiArg: {module: servicePath + "multi", fn: ""},
                        multiVarArg: {module: servicePath + "multi", fn: "multiVar", varArgs: 2},
                        multiVarArgIdent: {
                            module: servicePath + "multi",
                            fn: "multiIdent",
                            varArgs: 2,
                            allowIdentifier: true
                        },
                        ctor: {module: servicePath + "ctor", fn: "MyConstructor"},
                        contextTarget: {
                            module: servicePath + "contextTarget",
                            fn: "contextTarget",
                            captureContext: "parent.id.name"
                        },
                        arrayContextTarget: {
                            module: servicePath + "contextTarget",
                            fn: "arrayContextTarget",
                            captureContext: ["parent.id.name", "parent.left.name"]
                        },
                        objectContextTarget: {
                            module: servicePath + "contextTarget",
                            fn: "objectContextTarget",
                            captureContext: {
                                type: "parent.type",
                                name: "parent.parent.id.name"
                            }
                        }
                    },
                    indexes,
                    debug: false,
                    sourceRoot: "test-modules/"
                }
            ]
        ]
    };

    babel.transformSync(
        fs.readFileSync(path.join(import.meta.dirname, relPath), "utf-8"),
        {
            ...opts,
            filename: relPath,
            babelrc: false,
            configFile: false,
            parserOpts: {plugins: typeScript ? ["typescript"] : []}
        }
    );
}
