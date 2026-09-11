import {expect} from "vitest";

/**
 * The socket the module opens, under test control: nothing happens to it until the test says so, so
 * "connected", "a frame arrived" and "the connection dropped" are all things a test states rather than
 * waits for.
 */
export class FakeWebSocket
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


export function lastSocket(): FakeWebSocket
{
    const socket = FakeWebSocket.instances[FakeWebSocket.instances.length - 1]

    expect(socket, "no socket was opened").toBeDefined()

    return socket
}


/** Opens the socket the first subscription asked for and returns it. */
export function connected(): FakeWebSocket
{
    const socket = lastSocket()
    socket.open()
    return socket
}
