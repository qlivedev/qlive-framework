// @vitest-environment jsdom
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {init} from "../src/config";
import {and, field, value} from "../src/FilterDSL";
import {initPubSub, PubSubConnection, subscribeToTopic} from "../src/pubsub";
import {testConfig, testCsrfToken} from "./fixtures/testConfig";

const CONTEXT_PATH = "/qlive"

/**
 * The socket the module opens, under test control: nothing happens to it until the test says so, so
 * "connected", "a frame arrived" and "the connection dropped" are all things a test states rather than
 * waits for.
 */
class FakeWebSocket
{
    static CONNECTING = 0
    static OPEN = 1
    static CLOSING = 2
    static CLOSED = 3

    /**
     * Every socket the module has opened since the last reset, in order. A reconnect is visible here as a
     * second entry, which is the only way the test can tell one happened.
     */
    static instances: FakeWebSocket[] = []

    readyState = FakeWebSocket.CONNECTING

    readonly url: string

    readonly sent: string[] = []

    onopen: (() => void) | null = null

    onmessage: ((event: { data: string }) => void) | null = null

    onclose: (() => void) | null = null

    onerror: (() => void) | null = null


    constructor(url: string)
    {
        this.url = url
        FakeWebSocket.instances.push(this)
    }


    send(data: string): void
    {
        this.sent.push(data)
    }


    close(): void
    {
        this.drop()
    }


    // ---- test side ----

    /** Completes the handshake. */
    open(): void
    {
        this.readyState = FakeWebSocket.OPEN
        this.onopen?.()
    }


    /** Delivers one server frame. */
    receive(message: object): void
    {
        this.onmessage?.({data: JSON.stringify(message)})
    }


    /** Loses the connection, the way a restarting server does. */
    drop(): void
    {
        this.readyState = FakeWebSocket.CLOSED
        this.onclose?.()
    }


    /** What the module sent on this socket, parsed. */
    messages(): any[]
    {
        return this.sent.map(s => JSON.parse(s))
    }
}


function lastSocket(): FakeWebSocket
{
    const socket = FakeWebSocket.instances[FakeWebSocket.instances.length - 1]

    expect(socket, "no socket was opened").toBeDefined()

    return socket
}


/** Opens the socket the first subscription asked for and returns it. */
function connected(): FakeWebSocket
{
    const socket = lastSocket()
    socket.open()
    return socket
}


beforeEach(async () => {
    FakeWebSocket.instances = []
    vi.stubGlobal("WebSocket", FakeWebSocket)

    await init({
        config: {...testConfig, contextPath: CONTEXT_PATH},
        csrfToken: testCsrfToken(),
        data: {}
    })

    initPubSub()
})


afterEach(() => {
    initPubSub()
    vi.unstubAllGlobals()
    vi.useRealTimers()
})


describe("connecting", () => {

    it("opens no socket until something subscribes", () => {

        // The login-page case: startup() runs there too, and a socket opened for an entry point that
        // subscribes to nothing would only be refused by a handshake nobody is authenticated for.
        expect(FakeWebSocket.instances).toHaveLength(0)
        expect(PubSubConnection.getSnapshot().status).toBe("idle")
    })


    it("opens the push URI under the application's context path", () => {

        subscribeToTopic("Foo", () => {})

        expect(lastSocket().url).toBe(
            window.location.origin.replace(/^http/, "ws") + CONTEXT_PATH + "/push"
        )
    })


    it("opens one socket however many subscriptions there are", () => {

        subscribeToTopic("Foo", () => {})
        subscribeToTopic("Bar", () => {})

        expect(FakeWebSocket.instances).toHaveLength(1)
    })


    it("reports what the connection is doing", () => {

        const seen: string[] = []
        PubSubConnection.subscribe(() => seen.push(PubSubConnection.getSnapshot().status))

        subscribeToTopic("Foo", () => {})
        expect(PubSubConnection.getSnapshot().status).toBe("connecting")

        connected()
        expect(PubSubConnection.getSnapshot().status).toBe("connected")

        expect(seen).toEqual(["connecting", "connected"])
    })


    it("keeps the same snapshot while nothing changes", () => {

        const first = PubSubConnection.getSnapshot()

        expect(PubSubConnection.getSnapshot()).toBe(first)

        subscribeToTopic("Foo", () => {})

        // useSyncExternalStore compares by identity, so an unchanged connection has to keep handing out
        // the same object and a changed one a different one.
        expect(PubSubConnection.getSnapshot()).not.toBe(first)
    })
})


describe("subscribing", () => {

    it("sends the subscription once the socket is open", () => {

        subscribeToTopic("Foo", () => {})

        // Nothing can go out before the handshake completes.
        expect(lastSocket().messages()).toEqual([])

        const socket = connected()

        expect(socket.messages()).toEqual([
            {type: "Subscribe", topic: "Foo", id: "1", condition: null}
        ])
    })


    it("sends a condition as the wire form of the DSL", () => {

        subscribeToTopic(
            "EntityVersion",
            () => {},
            and(
                field("entityType").eq(value("Bar")),
                field("ownerId").ne(value("me"))
            )
        )

        const [subscribe] = connected().messages()

        expect(subscribe.condition).toEqual({
            type: "Condition",
            name: "and",
            operands: [
                {
                    type: "Condition",
                    name: "eq",
                    operands: [
                        {type: "Field", name: "entityType"},
                        {type: "Value", scalarType: "String", value: "Bar"}
                    ]
                },
                {
                    type: "Condition",
                    name: "ne",
                    operands: [
                        {type: "Field", name: "ownerId"},
                        {type: "Value", scalarType: "String", value: "me"}
                    ]
                }
            ]
        })
    })


    it("sends a subscription made after the socket is open straight away", () => {

        subscribeToTopic("Foo", () => {})
        const socket = connected()

        subscribeToTopic("Bar", () => {})

        expect(socket.messages().map(m => m.topic)).toEqual(["Foo", "Bar"])
    })


    it("unsubscribes and stops delivering", () => {

        const handler = vi.fn()
        const unsubscribe = subscribeToTopic("Foo", handler)
        const socket = connected()

        unsubscribe()

        expect(socket.messages()[1]).toEqual({type: "Unsubscribe", topic: "Foo", id: "1"})

        socket.receive({type: "Topic", topic: "Foo", ids: ["1"], payload: {name: "late"}})

        expect(handler).not.toHaveBeenCalled()
    })


    it("unsubscribes only once", () => {

        const unsubscribe = subscribeToTopic("Foo", () => {})
        const socket = connected()

        unsubscribe()
        unsubscribe()

        expect(socket.messages().filter(m => m.type === "Unsubscribe")).toHaveLength(1)
    })
})


describe("receiving", () => {

    it("hands a payload to the subscription that matched", () => {

        const foo = vi.fn()
        const bar = vi.fn()

        subscribeToTopic("Foo", foo)
        subscribeToTopic("Bar", bar)
        const socket = connected()

        socket.receive({type: "Topic", topic: "Foo", ids: ["1"], payload: {name: "one"}})

        expect(foo).toHaveBeenCalledWith({name: "one"})
        expect(bar).not.toHaveBeenCalled()
    })


    it("delivers a batched message to every subscription it names", () => {

        const first = vi.fn()
        const second = vi.fn()

        subscribeToTopic("Foo", first)
        subscribeToTopic("Foo", second)
        const socket = connected()

        // One publish matching two of this connection's subscriptions arrives once, naming both.
        socket.receive({type: "Topic", topic: "Foo", ids: ["1", "2"], payload: {name: "both"}})

        expect(first).toHaveBeenCalledWith({name: "both"})
        expect(second).toHaveBeenCalledWith({name: "both"})
    })


    it("ignores an id it no longer knows", () => {

        subscribeToTopic("Foo", () => {})
        const socket = connected()

        // An unsubscribe that crossed a publish in flight.
        expect(
            () => socket.receive({type: "Topic", topic: "Foo", ids: ["99"], payload: {}})
        ).not.toThrow()
    })


    it("does not let a throwing handler cost the others the message", () => {

        const thrower = vi.fn(() => { throw new Error("boom") })
        const other = vi.fn()
        vi.spyOn(console, "error").mockImplementation(() => {})

        subscribeToTopic("Foo", thrower)
        subscribeToTopic("Foo", other)
        const socket = connected()

        socket.receive({type: "Topic", topic: "Foo", ids: ["1", "2"], payload: {name: "one"}})

        expect(other).toHaveBeenCalledWith({name: "one"})
    })


    it("reports a refused subscription instead of waiting on it forever", () => {

        const error = vi.spyOn(console, "error").mockImplementation(() => {})

        subscribeToTopic("Nope", () => {})
        const socket = connected()

        socket.receive({type: "Error", topic: "Nope", id: "1", message: "No channel 'Nope'"})

        expect(error).toHaveBeenCalledWith(expect.stringContaining("No channel 'Nope'"))
    })


    it("survives a frame that is not JSON", () => {

        vi.spyOn(console, "error").mockImplementation(() => {})

        const handler = vi.fn()
        subscribeToTopic("Foo", handler)
        const socket = connected()

        socket.onmessage!({data: "not json"})
        socket.receive({type: "Topic", topic: "Foo", ids: ["1"], payload: {name: "after"}})

        expect(handler).toHaveBeenCalledWith({name: "after"})
    })
})


describe("reconnecting", () => {

    it("re-issues every subscription on a new socket, without the application doing anything", () => {

        vi.useFakeTimers()

        const handler = vi.fn()
        subscribeToTopic("Foo", handler, field("name").eq(value("x")))
        subscribeToTopic("Bar", () => {})
        const first = connected()

        const announced = first.messages()

        first.drop()
        vi.advanceTimersByTime(1000)

        expect(FakeWebSocket.instances).toHaveLength(2)

        const second = connected()

        // The same two Subscribe messages, ids and conditions included: the server knows the subscriptions
        // under the ids the client has been using all along.
        expect(second.messages()).toEqual(announced)

        second.receive({type: "Topic", topic: "Foo", ids: ["1"], payload: {name: "x"}})

        expect(handler).toHaveBeenCalledWith({name: "x"})
    })


    it("backs off further with every failed attempt", () => {

        vi.useFakeTimers()

        subscribeToTopic("Foo", () => {})
        lastSocket().drop()

        // Half the window is fixed and half is random, so the first retry lands somewhere in [250, 500).
        // The bounds below are the ones that hold for every draw rather than for most of them: a test of
        // jittered timing that passes by luck is worse than no test of it.
        vi.advanceTimersByTime(249)
        expect(FakeWebSocket.instances).toHaveLength(1)

        vi.advanceTimersByTime(251)
        expect(FakeWebSocket.instances).toHaveLength(2)

        lastSocket().drop()

        // The second window is twice the first -- [500, 1000) -- so what was enough above is not enough
        // here.
        vi.advanceTimersByTime(499)
        expect(FakeWebSocket.instances).toHaveLength(2)

        vi.advanceTimersByTime(501)
        expect(FakeWebSocket.instances).toHaveLength(3)
    })


    it("counts the attempts it is backing off over, and forgets them once connected", () => {

        vi.useFakeTimers()

        subscribeToTopic("Foo", () => {})
        lastSocket().drop()

        expect(PubSubConnection.getSnapshot()).toEqual({status: "reconnecting", attempts: 1})

        vi.advanceTimersByTime(1000)
        connected()

        expect(PubSubConnection.getSnapshot()).toEqual({status: "connected", attempts: 0})
    })


    it("does not reconnect for a connection nothing is subscribed on", () => {

        vi.useFakeTimers()

        const unsubscribe = subscribeToTopic("Foo", () => {})
        const socket = connected()

        unsubscribe()
        socket.drop()

        vi.advanceTimersByTime(60_000)

        expect(FakeWebSocket.instances).toHaveLength(1)
        expect(PubSubConnection.getSnapshot().status).toBe("idle")
    })


    it("drops the connection and its subscriptions on a fresh startup", () => {

        vi.useFakeTimers()

        subscribeToTopic("Foo", () => {})
        connected()

        initPubSub()

        expect(PubSubConnection.getSnapshot()).toEqual({status: "idle", attempts: 0})

        // Closed deliberately, so nothing reconnects for what the previous page had subscribed.
        vi.advanceTimersByTime(60_000)
        expect(FakeWebSocket.instances).toHaveLength(1)
    })
})
