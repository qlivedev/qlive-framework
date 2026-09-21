import {beforeEach, describe, expect, it} from "vitest";
import trackUsageData from "../../../src/vite/babel/trackUsageData.js";
import {transform} from "./transform";

/**
 * The same contract over TypeScript sources, which is what a QLive application
 * actually feeds the plugin.
 */
describe("Track Usage Plugin (Typescript)", () =>
{
    // reset collected data before each test
    beforeEach(() => trackUsageData.clear());

    it("detects module functions", () =>
    {

        transform("./test-modules/typescript/mod-fn.ts", true);

        const usages = trackUsageData.get().usages as Record<string, any>
        //console.log(JSON.stringify(usages, null, 2));
        expect(usages['./typescript/mod-fn'].requires[0]).toBe("./typescript/service/moduleFn");
        expect(usages['./typescript/mod-fn'].requires[1]).toBe("./typescript/service/nonVarMod");

        expect(usages['./typescript/mod-fn'].calls.moduleFn).toEqual([
                ["Foo"],
                ["NotIgnored"]
            ]);
        expect(usages['./typescript/mod-fn'].calls.nonVar).toEqual([
                ["A"],
                ["A", "B"]
            ]);

    });

    it("detects member functions", () =>
    {

        transform("./test-modules/typescript/member-fn.ts", true);

        const usages = trackUsageData.get().usages as Record<string, any>
        //console.log(JSON.stringify(usages, null, 2));

        expect(usages['./typescript/member-fn'].requires[0]).toBe("./typescript/service/lookup");
        expect(usages['./typescript/member-fn'].calls.lookup).toEqual([
                ["Bar"],
                ["Present"]
            ]);

        expect(usages['./typescript/member-fn'].calls.nvLookup).toEqual([
                ["A"]
            ]);
    })

    it("detects member functions as named imports", () =>
    {
        transform("./test-modules/typescript/member-fn-2.ts", true);

        const usages = trackUsageData.get().usages as Record<string, any>
        //console.log(JSON.stringify(usages, null, 2));

        expect(usages['./typescript/member-fn-2'].requires[0]).toBe("./typescript/service/lookup");
        expect(usages['./typescript/member-fn-2'].calls.lookup).toEqual([
                ["Bar"],
                ["Present"]
            ]);
        expect(usages['./typescript/member-fn-2'].calls.nvLookup).toEqual([
                ["A"]
            ]);
    })

    it("detects member functions with aliased import", () =>
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

        transform("./test-modules/typescript/multi.ts", true);

        const usages = trackUsageData.get().usages as Record<string, any>

        //console.log(JSON.stringify(usages, null, 2));
        expect(usages['./typescript/multi'].calls.multiArg).toEqual([
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

        expect(usages['./typescript/multi'].calls.multiVarArg).toEqual([
            [
                "Qux",
                "Quux"
            ]
            // no entry for "const g = multiVar('Ignored',  'Not Literal' + 5);"
        ]);
    })

    it("detects supports identifiers", () =>
    {

        transform("./test-modules/typescript/multi.ts", true);

        const usages = trackUsageData.get().usages as Record<string, any>

        //console.log(JSON.stringify(usages, null, 2));

        expect(usages['./typescript/multi'].calls.multiVarArgIdent).toEqual([
            [
                { __identifier: "MyIdent" }, { value: "abc"}
            ]
        ]);
    })


    it("supports template literals", () =>
    {
        transform("./test-modules/typescript/member-fn-template.ts", true);

        const usages = trackUsageData.get().usages as Record<string, any>
        //console.log(JSON.stringify(usages, null, 2));

        expect(usages['./typescript/member-fn-template'].requires[0]).toBe("./typescript/service/lookup");

        expect(usages['./typescript/member-fn-template'].calls.lookup).toEqual([
                [
                    "\n    Bar\n"
                ]
            ]);
    });

    it("does not support template literals with expressions", () =>
    {
        expect(() => transform("./test-modules/typescript/member-fn-template2.ts", true)).toThrow(/Extracted template literals can't contain expressions/);
    })


    it("does detect constructor calls", () =>
    {
        transform("./test-modules/typescript/ctor.ts", true);

        const usages = trackUsageData.get().usages as Record<string, any>
        //console.log(JSON.stringify(usages, null, 2));

        expect(usages['./typescript/ctor'].calls.ctor).toEqual([
            [
                "CTOR NAME",
            ]
        ]);

    })


});
