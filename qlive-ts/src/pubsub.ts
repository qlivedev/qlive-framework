import config from "./config";
import {FilterExpression, RawValue, toJSON} from "./FilterDSL";

/**
 * Where the push websocket is served, mirroring QLivePaths.PUSH_URI on the Java side. Both sides have to
 * agree on it and only one of them is Java.
 */
const PUSH_URI = "/push"

/**
 * First reconnect waits about this long, and every further attempt doubles the window until it reaches
 * RECONNECT_MAX_MS.
 */
const RECONNECT_BASE_MS = 500

const RECONNECT_MAX_MS = 30_000

/**
 * What the connection is doing, as far as an application has any use for knowing.
 *
 * "idle" covers both "nothing has subscribed yet" and "the last socket closed and nothing wants another
 * one", because to a view waiting for messages those are the same situation: none are coming.
 */
export type PubSubStatus = "idle" | "connecting" | "connected" | "reconnecting"

export interface PubSubConnectionSnapshot
{
    /**
     * "reconnecting" does not distinguish a server that is down from a session that has expired, and it
     * cannot: a browser reports a refused websocket handshake to script without a status code, so a 401
     * and a dead port arrive here as the same event. A page whose session is gone therefore sits in
     * "reconnecting" until something on the ordinary HTTP path -- the next query -- gets the 401 that can
     * be read, and the application does what it does about that. Worth knowing before this status is put
     * in front of a user as "connection lost".
     */
    status: PubSubStatus

    /**
     * Failed connection attempts since the last successful one, which is what the current backoff window is
     * derived from. Zero while connected.
     */
    attempts: number
}

/**
 * Called with the payload of every message published to the topic that the subscription's condition
 * matched.
 *
 * @typeParam T    what the channel carries. Supplied by the caller: a channel's payload class is a
 *                 Svenson-described Java class, and generating TypeScript for those is its own facility
 *                 (see docs/design/client-types.md). Until that exists, nothing here knows the type of a
 *                 channel by its name.
 */
export type TopicHandler<T> = (payload: T) => void

interface Subscription
{
    id: string
    topic: string

    /**
     * The condition as it goes on the wire, serialized once when the subscription is registered rather than
     * per send. A reconnect re-issues exactly what the first Subscribe carried, and the DSL instances the
     * caller built are not held past this call.
     */
    condition: RawValue | null

    handler: TopicHandler<any>
}

/**
 * Every live subscription, keyed by the id the server knows it under. This is the whole of what a reconnect
 * has to replay -- which is why resubscription is invisible to application code: the module never forgot
 * what was subscribed, only which socket the subscriptions had been announced on.
 */
let subscriptions = new Map<string, Subscription>()

let socket: WebSocket | null = null

let status: PubSubStatus = "idle"

let attempts = 0

let reconnectTimer: ReturnType<typeof setTimeout> | null = null

let idCounter = 0

let listeners: (() => void)[] = []

let snapshot: PubSubConnectionSnapshot | null = null


function notify(): void
{
    snapshot = null

    for (const listener of listeners)
    {
        listener()
    }
}


function setStatus(newStatus: PubSubStatus, newAttempts: number = attempts): void
{
    if (status === newStatus && attempts === newAttempts)
    {
        return
    }

    status = newStatus
    attempts = newAttempts
    notify()
}


/**
 * The connection state as a store, in the subscribe/getSnapshot shape QueryDocument and WorkingSet already
 * use -- so a view renders an offline indicator with useSyncExternalStore and nothing else.
 *
 * getSnapshot() returns the same object for as long as nothing changes, which is what
 * useSyncExternalStore's own change detection needs.
 */
export const PubSubConnection = {

    subscribe(fn: () => void): () => void
    {
        listeners.push(fn)

        return () =>
        {
            // replaces the list rather than splicing it, which is what lets a listener unsubscribe while
            // notify() is iterating
            listeners = listeners.filter(l => l !== fn)
        }
    },

    getSnapshot(): PubSubConnectionSnapshot
    {
        if (!snapshot)
        {
            snapshot = {
                status,
                attempts
            }
        }

        return snapshot
    }
}


/**
 * The URL the socket opens, built from the page's own origin and the servlet context path the way
 * util/graphql.ts builds its request URL -- so an application pays nothing extra in configuration to get
 * push once it is on QLive.
 *
 * The scheme is the page's, upgraded: an https page gets wss, so push never becomes the one plaintext
 * connection on a secured page. Same origin is also what carries the session cookie, which is the whole of
 * how the handshake authenticates.
 */
function pushUrl(): string
{
    const {contextPath} = config()

    return window.location.origin.replace(/^http/, "ws") + contextPath + PUSH_URI
}


function send(message: object): void
{
    socket!.send(JSON.stringify(message))
}


function sendSubscribe(subscription: Subscription): void
{
    send({
        type: "Subscribe",
        topic: subscription.topic,
        id: subscription.id,
        condition: subscription.condition
    })
}


/**
 * Backoff window for the given number of failed attempts, with jitter: half the window fixed, half of it
 * random. A server coming back up is met by clients spread over that window instead of by all of them at
 * once.
 */
function reconnectDelay(attempt: number): number
{
    const window = Math.min(RECONNECT_MAX_MS, RECONNECT_BASE_MS * 2 ** attempt)

    return window / 2 + Math.random() * window / 2
}


function scheduleReconnect(): void
{
    const delay = reconnectDelay(attempts)

    setStatus("reconnecting", attempts + 1)

    reconnectTimer = setTimeout(
        () =>
        {
            reconnectTimer = null
            connect()
        },
        delay
    )
}


function onMessage(event: MessageEvent): void
{
    let message: any

    try
    {
        message = JSON.parse(event.data)
    }
    catch (e)
    {
        console.error("Push: could not parse frame", event.data, e)
        return
    }

    switch (message.type)
    {
        case "Topic":
            // console.log("[DEBUG push] Topic", message)
            // One outgoing message can name several of this connection's subscriptions: a publish matching
            // more than one of them is batched rather than sent once per match.
            for (const id of message.ids)
            {
                const subscription = subscriptions.get(id)
                if (!subscription)
                {
                    // An unsubscribe that crossed a publish in flight. Nothing to do, and nothing wrong.
                    continue
                }

                try
                {
                    subscription.handler(message.payload)
                }
                catch (e)
                {
                    // One handler throwing must not cost the other subscriptions this message, nor the
                    // connection its message loop.
                    console.error(`Push: handler for '${ subscription.topic }' failed`, e)
                }
            }
            break

        case "Subscribed":
            // console.log("[DEBUG push] Subscribed", message)
            // Nothing to do with it -- but the other outcome is an Error naming what was refused, and that
            // distinction only exists because this is acknowledged at all.
            break

        case "Error":
            console.error(
                `Push: server refused '${ message.topic }'/'${ message.id }': ${ message.message }`
            )
            break

        default:
            console.warn("Push: unhandled message kind", message)
    }
}


/**
 * Opens the socket, unless one is already open or opening.
 *
 * Called on the first subscription and on every reconnect, never by application code: what a subscriber
 * asks for is messages, and the connection carrying them is this module's business.
 */
function connect(): void
{
    if (socket)
    {
        return
    }

    setStatus(attempts === 0 ? "connecting" : "reconnecting")

    const ws = new WebSocket(pushUrl())
    socket = ws

    ws.onopen = () =>
    {
        setStatus("connected", 0)

        for (const subscription of subscriptions.values())
        {
            sendSubscribe(subscription)
        }
    }

    ws.onmessage = onMessage

    ws.onclose = () =>
    {
        // Guards against a socket that was replaced by disconnect() reporting its own close afterwards and
        // tearing down its successor.
        if (socket !== ws)
        {
            return
        }
        socket = null

        if (subscriptions.size === 0)
        {
            // Nothing is waiting for messages, so there is nothing to reconnect for. The next
            // subscribeToTopic() opens a socket again.
            setStatus("idle", 0)
            return
        }

        scheduleReconnect()
    }

    ws.onerror = () =>
    {
        // Deliberately quiet: the browser logs the failed connection itself, and onclose follows every
        // error, which is where the reconnect belongs.
    }
}


/**
 * Subscribes to a channel, connecting if this is the first subscription.
 *
 * The returned function unsubscribes. A subscription lives until it does: a dropped connection does not
 * end one, because this module re-issues every registered subscription on reconnect -- an application
 * subscribes once and keeps receiving messages across a server restart without doing anything about it.
 *
 * @param topic        name of the channel, as the server registered it
 * @param handler      called with the payload of every message matching the condition
 * @param condition    FilterDSL condition every message on the channel is evaluated against before it is
 *                     delivered here, or null to receive everything the channel carries. Field paths are
 *                     resolved against the channel's payload, and a to-many hop in one reads positionally
 *                     (`bazLinks.0.baz.name`), unlike the same DSL against the database.
 *
 * @returns a function that unsubscribes
 */
export function subscribeToTopic<T>(
    topic: string,
    handler: TopicHandler<T>,
    condition: FilterExpression | null = null
): () => void
{
    const id = String(++idCounter)

    // Uncomment with: import {decompileFilter} from "./util/decompileFilter"
    // console.log("[DEBUG push] subscribeToTopic", topic, id, "\n" + decompileFilter(condition))

    const subscription: Subscription = {
        id,
        topic,
        condition: condition ? toJSON(condition) : null,
        handler
    }

    subscriptions.set(id, subscription)

    if (socket && socket.readyState === WebSocket.OPEN)
    {
        sendSubscribe(subscription)
    }
    else
    {
        // Either there is no socket yet, or one is still opening -- in which case its onopen sends every
        // registered subscription, this one included.
        connect()
    }

    return () =>
    {
        if (!subscriptions.delete(id))
        {
            return
        }

        if (socket && socket.readyState === WebSocket.OPEN)
        {
            send({
                type: "Unsubscribe",
                topic,
                id
            })
        }
    }
}


/**
 * Resets the module and drops any connection it holds.
 *
 * Called by startup(). The socket itself is not opened here: startup() also runs on entry points that
 * never subscribe to anything -- a login page being the obvious one, and one whose handshake would be
 * refused for not being authenticated yet -- so connecting there would buy a doomed socket and a backoff
 * loop behind it. The first subscribeToTopic() opens the connection instead, which on an application view
 * is the same moment by every measure that matters.
 */
export function initPubSub(): void
{
    disconnect()

    subscriptions = new Map()
    idCounter = 0
    setStatus("idle", 0)
}


function disconnect(): void
{
    if (reconnectTimer)
    {
        clearTimeout(reconnectTimer)
        reconnectTimer = null
    }

    if (socket)
    {
        const ws = socket
        // Cleared first: the close below fires onclose, and this socket is not one to reconnect for.
        socket = null
        ws.close()
    }
}
