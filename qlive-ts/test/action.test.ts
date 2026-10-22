import {afterEach, beforeAll, describe, expect, it, vi} from "vitest";
import {Temporal} from "temporal-polyfill";
import {init, QLiveConfig} from "../src/config";
import {issueAction, refusalMessage, refusalsOf, Refusal, Subject} from "../src/action";
import {GraphQLResponseError} from "../src/util/graphql";
import {
    field,
    inputObject,
    inputValue,
    LIST_OF,
    NAMED,
    NOT_NULL,
    object,
    scalar,
    STRING,
    testAuthentication,
    testConfig,
    testCsrfToken,
    TIMESTAMP
} from "./fixtures/testConfig";
import {respondWith, sentVariables} from "./fixtures/graphqlMock";

type ApproveResult = {
    barId?: string
    approved?: Temporal.Instant
}

declare module "../src/action" {
    interface Actions {
        approveBar: { args: { bar: Subject, comment: string, when?: Temporal.Instant | null }, result: ApproveResult }
        approveBars: { args: { bars: Subject[] }, result: ApproveResult[] }
        countBars: { args: {}, result: number }
    }
}

/**
 * The test schema with the mutations of three actions: one subject with input, a list subject, and no argument and
 * a scalar result.
 */
const actionConfig: QLiveConfig = {
    ...testConfig,
    schema: {
        types: [
            ...testConfig.schema.types.filter(t => t.name !== "MutationType"),
            scalar("Boolean"),
            inputObject("SubjectRefInput", [
                inputValue("id", NOT_NULL(STRING)),
                inputValue("version", STRING)
            ]),
            object("ApproveResult", [
                field("barId", STRING),
                field("approved", TIMESTAMP),
                field("owner", NAMED("AppUser", "OBJECT"))
            ]),
            object("MutationType", [
                {
                    ...field("approveBar", NOT_NULL(NAMED("ApproveResult", "OBJECT"))),
                    args: [
                        inputValue("bar", NOT_NULL(NAMED("SubjectRefInput", "INPUT_OBJECT"))),
                        inputValue("comment", NOT_NULL(STRING)),
                        inputValue("when", TIMESTAMP)
                    ]
                },
                {
                    ...field("approveBars", NOT_NULL(LIST_OF(NAMED("ApproveResult", "OBJECT")))),
                    args: [
                        inputValue("bars", NOT_NULL(LIST_OF(NOT_NULL(NAMED("SubjectRefInput", "INPUT_OBJECT")))))
                    ]
                },
                field("countBars", NAMED("Int"))
            ])
        ]
    }
}

const BAR = {id: "bar-1", version: "v1", name: "Bar #1", description: "described"}

function refusal(code: string, messageKey: string, field: string | null): Refusal
{
    return {code, messageKey, field, errorClass: null}
}

function refusedWith(...refusals: Refusal[])
{
    return {
        data: null,
        errors: refusals.map(r => ({
            message: r.messageKey,
            path: ["approveBar"],
            extensions: {...r, classification: "REFUSED"}
        }))
    }
}

beforeAll(async () => {
    await init({
        config: actionConfig,
        csrfToken: testCsrfToken(),
        authentication: testAuthentication(),
        data: {}
    })
})

afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
})

describe("issueAction", () => {

    it("builds the mutation from the schema, selecting the result's scalar fields", async () => {
        const fetchMock = respondWith({data: {approveBar: {barId: "bar-1", approved: null}}, errors: []})

        await issueAction("approveBar", {bar: BAR, comment: "fine"})

        const {query} = JSON.parse(fetchMock.mock.calls[0][1].body)
        expect(query).toBe(
            "mutation approveBar($bar: SubjectRefInput!, $comment: String!, $when: Timestamp) " +
            "{ approveBar(bar: $bar, comment: $comment, when: $when) { barId approved } }"
        )
    })

    it("sends a subject as its id and version, and converts the input", async () => {
        const fetchMock = respondWith({data: {approveBar: {barId: "bar-1", approved: null}}, errors: []})

        await issueAction("approveBar", {
            bar: BAR,
            comment: "fine",
            when: Temporal.Instant.from("2026-10-22T10:00:00Z")
        })

        expect(sentVariables(fetchMock)).toEqual({
            bar: {id: "bar-1", version: "v1"},
            comment: "fine",
            when: "2026-10-22T10:00:00Z"
        })
    })

    it("sends every row of a list subject", async () => {
        const fetchMock = respondWith({data: {approveBars: []}, errors: []})

        await issueAction("approveBars", {bars: [BAR, {id: "bar-2", version: "v7"}]})

        expect(sentVariables(fetchMock).bars).toEqual([{id: "bar-1", version: "v1"}, {id: "bar-2", version: "v7"}])
    })

    it("resolves with the converted result", async () => {
        respondWith({data: {approveBar: {barId: "bar-1", approved: "2026-10-22T10:00:00Z"}}, errors: []})

        const outcome = await issueAction("approveBar", {bar: BAR, comment: "fine"})

        expect(outcome.refused).toBe(false)
        if (!outcome.refused)
        {
            expect(outcome.result.barId).toBe("bar-1")
            expect(outcome.result.approved).toBeInstanceOf(Temporal.Instant)
        }
    })

    it("takes an action without arguments and with a scalar result", async () => {
        const fetchMock = respondWith({data: {countBars: 3}, errors: []})

        const outcome = await issueAction("countBars", {})

        expect(JSON.parse(fetchMock.mock.calls[0][1].body).query).toBe("mutation countBars { countBars }")
        expect(outcome).toEqual({refused: false, result: 3})
    })

    it("resolves a refusal as an outcome, without logging an error", async () => {
        const consoleError = vi.spyOn(console, "error")
        respondWith(refusedWith(
            refusal("RULE", "Bar.descriptionRequired", "bar.description"),
            refusal("RULE", "Bar.commentWhenNotifying", "comment")
        ))

        const outcome = await issueAction("approveBar", {bar: BAR, comment: ""})

        expect(outcome).toEqual({
            refused: true,
            refusals: [
                refusal("RULE", "Bar.descriptionRequired", "bar.description"),
                refusal("RULE", "Bar.commentWhenNotifying", "comment")
            ]
        })
        expect(consoleError).not.toHaveBeenCalled()
    })

    it("rejects errors that are no refusal", async () => {
        respondWith({data: null, errors: [{message: "broken", extensions: {classification: "DataFetchingException"}}]})

        await expect(issueAction("approveBar", {bar: BAR, comment: "fine"})).rejects.toBeInstanceOf(GraphQLResponseError)
    })

    it("refuses a name the schema has no mutation of", async () => {
        // @ts-expect-error: not an action, which the generated map would have said already
        await expect(issueAction("noSuchAction", {})).rejects.toThrowError(/No action 'noSuchAction'/)
    })
})


describe("refusals", () => {

    it("are found by the field they blame or a field below it", () => {
        const refusals = [
            refusal("CONFLICT", "Action.subjectChanged", "bars.1"),
            refusal("RULE", "Bar.descriptionRequired", "bars.2.description"),
            refusal("RULE", "Bar.commentWhenNotifying", "comment")
        ]

        expect(refusalsOf(refusals, "bars.2")).toEqual([refusals[1]])
        expect(refusalsOf(refusals, "bars.1")).toEqual([refusals[0]])
        expect(refusalsOf(refusals, "comment")).toEqual([refusals[2]])
        expect(refusalsOf(refusals, "bar")).toEqual([])
    })

    it("read as the translation of their key", () => {
        // no translations in the test config, so each renders as its tag
        expect(refusalMessage(refusal("CONFLICT", "Action.subjectChanged", "bar"))).toBe("[Action.subjectChanged]")
        expect(refusalMessage(refusal("RULE", "Bar.descriptionRequired", null))).toBe("[Bar.descriptionRequired]")
    })
})
