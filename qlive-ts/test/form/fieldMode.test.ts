import {beforeAll, describe, expect, it} from "vitest";
import {init} from "../../src/config";
import {defaultFieldMode} from "../../src/form/fieldMode";
import {mergeConfig} from "../fixtures/mergeConfig";
import {testAuthentication} from "../fixtures/testConfig";

/**
 * Which fields a form edits where nothing says otherwise. The merge domain serves because it has what the rule
 * looks at: versioned types, foreign keys, and a link type keyed by two fields once it is given that key.
 */

beforeAll(async () => {
    await init({
        config: {
            ...mergeConfig,
            meta: {
                ...mergeConfig.meta,
                types: {
                    ...mergeConfig.meta.types,
                    Bar: {
                        ...mergeConfig.meta.types.Bar,
                        fields: {
                            created: {editable: false},
                            description: {computed: true},
                            num: {maxLength: 3}
                        }
                    },
                    CorgeLink: {
                        meta: {
                            uniqueKeys: [
                                {name: "pk_corge_link", fields: ["corgeId", "graultId"], primary: true, nullable: false}
                            ]
                        }
                    }
                }
            }
        },
        csrfToken: mergeConfig.csrfToken!,
        authentication: testAuthentication(),
        data: {}
    })
})

describe("defaultFieldMode()", () => {

    it("edits an ordinary field", () => {

        expect(defaultFieldMode("Bar", "name")).toBe("normal")
        expect(defaultFieldMode("Bar", "num")).toBe("normal")
    })

    it("shows the primary key as text", () => {

        expect(defaultFieldMode("Bar", "id")).toBe("text")
    })

    it("takes the primary key from the unique keys where there are some", () => {

        expect(defaultFieldMode("CorgeLink", "corgeId")).toBe("text")
        expect(defaultFieldMode("CorgeLink", "graultId")).toBe("text")
        expect(defaultFieldMode("CorgeLink", "id")).toBe("normal")
    })

    it("shows the version as text", () => {

        expect(defaultFieldMode("Bar", "version")).toBe("text")
    })

    it("shows a computed field as text", () => {

        expect(defaultFieldMode("Bar", "description")).toBe("text")
    })

    it("shows a field flagged as not editable as text", () => {

        expect(defaultFieldMode("Bar", "created")).toBe("text")
    })

    it("edits a foreign key", () => {

        expect(defaultFieldMode("Foo", "ownerId")).toBe("normal")
    })
})
