import {beforeEach, describe, expect, it} from "vitest";
import config, {init} from "../src/config";
import i18n from "../src/i18n";
import {testAuthentication, testConfig, testCsrfToken} from "./fixtures/testConfig";

beforeEach(async () => {
    await init({config: {...testConfig}, csrfToken: testCsrfToken(), authentication: testAuthentication(), data: {}})
})

describe("i18n", () => {

    it("translates a tag, filling in its arguments by position", () => {
        config().translations = {"Foo.name": "Name", "{1} of {0}": "{1} von {0}"}

        expect(i18n("Foo.name")).toBe("Name")
        expect(i18n("{1} of {0}", "Foo", "name")).toBe("name von Foo")
    })

    it("keeps a placeholder there is no argument for", () => {
        config().translations = {"Page {0} of {1}": "Page {0} of {1}"}

        expect(i18n("Page {0} of {1}", "3")).toBe("Page 3 of {1}")
    })

    it("shows a tag without translation in brackets, with its arguments", () => {
        config().translations = {}

        expect(i18n("Filter {0}", "Name")).toBe("[Filter {0}:Name]")
        expect(i18n("No rows")).toBe("[No rows]")
    })
})
