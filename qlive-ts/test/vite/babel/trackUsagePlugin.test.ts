import {beforeEach, describe, expect, it} from "vitest";
import trackUsageData from "../../../src/vite/babel/trackUsageData.js";
import {transform} from "./transform";

/**
 * The plugin's own suite, vendored with it. What it asserts is the contract the
 * vite plugin is built on: which imports a module is recorded as requiring, and
 * which calls are extracted under which symbolic name.
 */
describe("Track Usage Plugin", () =>
{
    // reset collected data before each test
    beforeEach(() => trackUsageData.clear());

    describe("ES5 require", () =>
    {
        it("detects module functions", () =>
        {

            transform("./test-modules/mod-fn-es5.js");

            const usages = trackUsageData.get().usages as Record<string, any>
            //console.log(JSON.stringify(usages, null, 2));
            expect(usages['./mod-fn-es5'].requires[0]).toBe("./service/moduleFn");
            expect(usages['./mod-fn-es5'].requires[1]).toBe("./service/nonVarMod");

            expect(usages['./mod-fn-es5'].calls.moduleFn).toEqual([
                    ["Foo"],
                    ["NotIgnored"]
                ]);
            expect(usages['./mod-fn-es5'].calls.nonVar).toEqual([
                    ["A"]
                ]);

        });

        it("detects member functions", () =>
        {

            transform("./test-modules/member-fn-es5.js");

            const usages = trackUsageData.get().usages as Record<string, any>
            //console.log(JSON.stringify(usages, null, 2));

            expect(usages['./member-fn-es5'].requires[0]).toBe("./service/lookup");
            expect(usages['./member-fn-es5'].calls.lookup).toEqual([
                    ["Bar"],
                    ["Present"]
                ]);

            expect(usages['./member-fn-es5'].calls.nvLookup).toEqual([
                    ["A"]
                ]);
        })

    });

    describe("ES6 modules", () =>
    {
        it("detects module functions", () =>
        {

            transform("./test-modules/mod-fn-es6.js");

            const usages = trackUsageData.get().usages as Record<string, any>
            //console.log(JSON.stringify(usages, null, 2));
            expect(usages['./mod-fn-es6'].requires[0]).toBe("./service/moduleFn");
            expect(usages['./mod-fn-es6'].requires[1]).toBe("./service/nonVarMod");

            expect(usages['./mod-fn-es6'].calls.moduleFn).toEqual([
                    ["Foo"],
                    ["NotIgnored"]
                ]);
            expect(usages['./mod-fn-es6'].calls.nonVar).toEqual([
                    ["A"],
                    ["A","B"],
                ]);
        });

        it("detects member functions", () =>
        {

            transform("./test-modules/member-fn-es6.js");

            const usages = trackUsageData.get().usages as Record<string, any>
            //console.log(JSON.stringify(usages, null, 2));

            expect(usages['./member-fn-es6'].requires[0]).toBe("./service/lookup");

            expect(usages['./member-fn-es6'].calls.lookup).toEqual([
                    ["Bar"],
                    ["Present"]
                ]);

            expect(usages['./member-fn-es6'].calls.nvLookup).toEqual([
                    ["A"]
                ]);
        });

        it("detects member functions with variable binding", () =>
        {

            transform("./test-modules/member-fn-es6-2.js");

            const usages = trackUsageData.get().usages as Record<string, any>
            //console.log(JSON.stringify(usages, null, 2));

            expect(usages['./member-fn-es6-2'].requires[0]).toBe("./service/lookup");
            expect(usages['./member-fn-es6-2'].calls.lookup).toEqual([
                    ["Bar"],
                    ["Present"]
                ]);
            expect(usages['./member-fn-es6-2'].calls.nvLookup).toEqual([
                    ["A"]
                ]);
        });

        it("detects member functions with aliased binding", () =>
        {

            transform("./test-modules/member-fn-es6-alias.js");

            const usages = trackUsageData.get().usages as Record<string, any>
            //console.log(JSON.stringify(usages, null, 2));

            expect(usages['./member-fn-es6-alias'].requires[0]).toBe("./service/lookup");
            expect(usages['./member-fn-es6-alias'].calls.lookup).toEqual([
                    ["Bar"],
                    ["Present"]
                ]);
            expect(usages['./member-fn-es6-alias'].calls.nvLookup).toEqual([
                    ["A"]
                ]);
        })

        it("detects multiple arguments", () =>
        {

            transform("./test-modules/multi.js");

            const usages = trackUsageData.get().usages as Record<string, any>
            //console.log(JSON.stringify(usages, null, 2));

            expect(usages['./multi'].calls.multiArg).toEqual([
                [
                    "Foo",
                    1
                ],
                [
                    "Bar",
                    "xxx",
                    2
                ],
                [
                    "Baz",
                    {
                        baz: 3,
                        "name": "Bazi",
                        obj: {
                            complex: true
                        }
                    }
                ],
                [],
                [
                    "Blubb",
                    {
                        values: [
                            "A",
                            "B",
                            "C"
                        ]
                    }
                ],
                [
                    'JustArray', ["1", "2", "3"]
                ],
                [
                    'Blafusel', { value: null}
                ]

            ]);

            expect(usages['./multi'].calls.multiVarArg).toEqual([
                [
                    "Qux",
                    "Quux"
                ]
                // no entry for "const g = multiVar('Ignored',  'Not Literal' + 5);"
            ]);

        })

        it("supports identifiers", () =>
        {

            transform("./test-modules/multi.js", false, true);

            const usages = trackUsageData.get().usages as Record<string, any>
            //console.log(JSON.stringify(usages, null, 2));
            expect(usages['./multi'].calls.multiVarArgIdent).toEqual([
                [
                    { __identifier: "MyIdent" }, { value: "abc"}
                ]
            ]);
        })

        it("captures context", () =>
        {

            transform("./test-modules/context.js", false, false);

            const usages = trackUsageData.get().usages as Record<string, any>
            //console.log(JSON.stringify(usages, null, 2));
            expect(usages['./context'].calls.contextTarget).toEqual([
                [
                    "PARAMS",
                    12
                ]
            ]);
            expect(usages['./context'].calls.arrayContextTarget).toEqual([
                [
                    "PARAMS",
                    34
                ],
                [
                    "PARAMS",
                    35
                ]
            ]);
            expect(usages['./context'].calls.objectContextTarget).toEqual([
                [
                    "PARAMS",
                    56
                ]
            ]);

            expect(usages['./context'].contexts.contextTarget).toEqual([
                "aaa"
            ]);

            expect(usages['./context'].contexts.arrayContextTarget).toEqual([
                [
                    "__FAILED__",
                    "bbb"
                ],
                [
                    "ccc",
                    "__FAILED__"
                ]
            ]);

            // The call sits in the default of an object pattern, so its parent is the
            // AssignmentPattern and there is no id above it to name. Upstream recorded
            // "ConditionalExpression"/"foos" here because its .babelrc applied
            // @babel/preset-env to the fixture before the plugin saw it, rewriting the
            // default into a conditional. This is the shape QLive gets: trackUsage.ts
            // parses with babelrc and configFile off, and transforms nothing.
            expect(usages['./context'].contexts.objectContextTarget).toEqual([
                {
                    "type": "AssignmentPattern",
                    "name": "__FAILED__"
                }
            ]);
        })

    });

    it("supports sub directories", () =>
    {

        transform("./test-modules/sub/mod-fn-es6.js");

        const usages = trackUsageData.get().usages as Record<string, any>
        //console.log(JSON.stringify(usages, null, 2));
        expect(usages['./sub/mod-fn-es6'].requires[0]).toBe("./service/moduleFn");
        expect(usages['./sub/mod-fn-es6'].requires[1]).toBe("./service/nonVarMod");

        expect(usages['./sub/mod-fn-es6'].calls.moduleFn).toEqual([
                ["Foo"],
                ["NotIgnored"]
            ]);

        expect(usages['./sub/mod-fn-es6'].calls.nonVar).toEqual([
                ["A"]
            ]);
    });

    it("supports template literals", () =>
    {
        transform("./test-modules/member-fn-es6-template.js");

        const usages = trackUsageData.get().usages as Record<string, any>
        //console.log(JSON.stringify(usages, null, 2));

        expect(usages['./member-fn-es6-template'].requires[0]).toBe("./service/lookup");

        expect(usages['./member-fn-es6-template'].calls.lookup).toEqual([
                [
                    "\n    Bar\n"
                ]
            ]);
    });

    it("does not support template literals with expressions", () =>
    {
        expect(() => transform("./test-modules/member-fn-es6-template2.js")).toThrow(/Extracted template literals can't contain expressions/);
    })

    it("does detect constructor calls", () =>
    {
        transform("./test-modules/ctor.js", false);

        const usages = trackUsageData.get().usages as Record<string, any>
        //console.log(JSON.stringify(usages, null, 2));

        expect(usages['./ctor'].calls.ctor).toEqual([
            [
                "CTOR NAME",
            ]
        ]);

    })

    it("optionally tracks indexes", () =>
    {
        transform("./test-modules/ctor.js", false, true);

        const usages = trackUsageData.get().usages as Record<string, any>

        //console.log(JSON.stringify(usages, null, 2));

        expect(usages['./ctor'].indexes.ctor).toEqual([
            [
                92,
                122
            ]
        ]);

    })

});
