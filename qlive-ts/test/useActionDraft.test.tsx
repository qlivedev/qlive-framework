// @vitest-environment jsdom
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, Root} from "react-dom/client";
import {init, QLiveConfig} from "../src/config";
import {Refusal, Subject} from "../src/action";
import {ActionDraft, useActionDraft} from "../src/useActionDraft";
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
    testCsrfToken
} from "./fixtures/testConfig";
import {respondWith, sentVariables} from "./fixtures/graphqlMock";

/**
 * The action a view holds: the arguments its parts set, what is still missing, and the outcome of issuing them.
 * Issuing itself is issueAction()'s, tested on its own.
 */

type ReviewResult = {
    barId?: string
}

declare module "../src/action" {
    interface Actions {
        reviewBar: { args: { bar: Subject, comment: string, notify: boolean, note?: string | null }, result: ReviewResult }
        reviewBars: { args: { bars: Subject[] }, result: ReviewResult[] }
    }
}

const reviewConfig: QLiveConfig = {
    ...testConfig,
    schema: {
        types: [
            ...testConfig.schema.types.filter(t => t.name !== "MutationType"),
            scalar("Boolean"),
            inputObject("SubjectRefInput", [
                inputValue("id", NOT_NULL(STRING)),
                inputValue("version", STRING)
            ]),
            object("ReviewResult", [
                field("barId", STRING)
            ]),
            object("MutationType", [
                {
                    ...field("reviewBar", NOT_NULL(NAMED("ReviewResult", "OBJECT"))),
                    args: [
                        inputValue("bar", NOT_NULL(NAMED("SubjectRefInput", "INPUT_OBJECT"))),
                        inputValue("comment", NOT_NULL(STRING)),
                        inputValue("notify", NOT_NULL(NAMED("Boolean"))),
                        inputValue("note", STRING)
                    ]
                },
                {
                    ...field("reviewBars", NOT_NULL(LIST_OF(NAMED("ReviewResult", "OBJECT")))),
                    args: [
                        inputValue("bars", NOT_NULL(LIST_OF(NOT_NULL(NAMED("SubjectRefInput", "INPUT_OBJECT")))))
                    ]
                }
            ])
        ]
    }
}

const BAR = {id: "bar-1", version: "v1", name: "Bar #1"}

/** the state the last render was given, and how often the view rendered */
let review: ActionDraft<"reviewBar">
let reviews: ActionDraft<"reviewBars">
let renderCount = 0

function ReviewView()
{
    review = useActionDraft("reviewBar")
    reviews = useActionDraft("reviewBars", {bars: []})
    renderCount++
    return null
}

let container: HTMLDivElement
let root: Root

beforeAll(async () => {
    (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true
    await init({
        config: reviewConfig,
        csrfToken: testCsrfToken(),
        authentication: testAuthentication(),
        data: {}
    })
})

beforeEach(() => {
    container = document.createElement("div")
    root = createRoot(container)
    renderCount = 0
    act(() => root.render(<ReviewView/>))
})

afterEach(() => {
    act(() => root.unmount())
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
})

describe("useActionDraft", () => {

    it("starts with a required Boolean false and the other required parameters missing", () => {
        expect(review.args).toEqual({notify: false})
        expect(review.missing).toEqual(["bar", "comment"])
        expect(review.complete).toBe(false)
        expect(review.outcome).toBeNull()
    })

    it("is complete once every required parameter has an argument", () => {
        act(() => review.set("bar", BAR))
        expect(review.missing).toEqual(["comment"])

        // an empty text is an argument; whether it is enough is a rule's to say
        act(() => review.set("comment", ""))
        expect(review.complete).toBe(true)
    })

    it("counts a list subject without rows as missing", () => {
        expect(reviews.missing).toEqual(["bars"])

        act(() => reviews.set("bars", [BAR]))
        expect(reviews.complete).toBe(true)
    })

    it("re-renders with a new state for a change and keeps it otherwise", () => {
        const before = review
        const renders = renderCount

        act(() => review.set("comment", "fine"))

        expect(renderCount).toBe(renders + 1)
        expect(review).not.toBe(before)
        expect(review.args.comment).toBe("fine")
    })

    it("drops the outcome for other rows, and keeps it for the same rows read again", async () => {
        respondWith({
            data: null,
            errors: [{
                message: "Action.subjectChanged",
                path: ["reviewBar"],
                extensions: {code: "CONFLICT", messageKey: "Action.subjectChanged", field: "bar", classification: "REFUSED"}
            }]
        })
        act(() => {
            review.set("bar", BAR)
            review.set("comment", "fine")
        })
        await act(() => review.issue())

        act(() => review.set("bar", {...BAR, version: "v2"}))
        expect(review.refusalsOf("bar")).toHaveLength(1)

        act(() => review.set("bar", {id: "bar-2", version: "v1"}))
        expect(review.outcome).toBeNull()
    })

    it("refuses a parameter the action does not have", () => {
        // @ts-expect-error: the generated map says so already
        expect(() => review.set("comentt", "fine")).toThrowError(/no parameter 'comentt'/)
    })

    it("issues the arguments set and keeps the outcome", async () => {
        const fetchMock = respondWith({data: {reviewBar: {barId: "bar-1"}}, errors: []})

        act(() => {
            review.set("bar", BAR)
            review.set("comment", "fine")
        })

        let issue!: Promise<unknown>
        act(() => {
            issue = review.issue()
        })
        expect(review.pending).toBe(true)

        await act(() => issue)

        expect(sentVariables(fetchMock)).toEqual({bar: {id: "bar-1", version: "v1"}, comment: "fine", notify: false})
        expect(review.pending).toBe(false)
        expect(review.outcome).toEqual({refused: false, result: {barId: "bar-1"}})
    })

    it("sends one request for an issue while one is pending", async () => {
        const fetchMock = respondWith({data: {reviewBar: {barId: "bar-1"}}, errors: []})
        act(() => {
            review.set("bar", BAR)
            review.set("comment", "fine")
        })

        let first!: Promise<unknown>
        let second!: Promise<unknown>
        act(() => {
            first = review.issue()
            second = review.issue()
        })
        await act(() => Promise.all([first, second]))

        expect(fetchMock).toHaveBeenCalledTimes(1)
        expect(second).toBe(first)
    })

    it("rejects an incomplete action without a request", async () => {
        const fetchMock = respondWith({data: {reviewBar: {barId: "bar-1"}}, errors: []})

        await expect(review.issue()).rejects.toThrowError(/'reviewBar' is missing 'bar', 'comment'/)
        expect(fetchMock).not.toHaveBeenCalled()
    })

    it("finds the refusals of the last issue by path", async () => {
        const refusals: Refusal[] = [
            {code: "RULE", messageKey: "Bar.descriptionRequired", field: "bar.description", errorClass: null},
            {code: "RULE", messageKey: "Bar.commentWhenNotifying", field: "comment", errorClass: null}
        ]
        respondWith({
            data: null,
            errors: refusals.map(r => ({
                message: r.messageKey,
                path: ["reviewBar"],
                extensions: {...r, classification: "REFUSED"}
            }))
        })
        act(() => {
            review.set("bar", BAR)
            review.set("comment", "")
        })

        await act(() => review.issue())

        expect(review.outcome?.refused).toBe(true)
        expect(review.refusalsOf("bar")).toEqual([refusals[0]])
        expect(review.refusalsOf("comment")).toEqual([refusals[1]])
        expect(review.refusalsOf("note")).toEqual([])
    })
})
