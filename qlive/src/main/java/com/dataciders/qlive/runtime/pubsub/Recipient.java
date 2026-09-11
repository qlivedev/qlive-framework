package com.dataciders.qlive.runtime.pubsub;

import com.dataciders.qlive.model.push.ServerMessage;

/// Somewhere a message can be delivered to.
///
/// A websocket connection is one, and the only one the transport makes. A framework or application bean
/// that wants to hear what is published on a channel without a browser being involved is the other, and it
/// is the reason this is an interface rather than the connection itself -- an in-process listener
/// subscribes through the same call a connection does, and the pub/sub core never learns which it is
/// talking to.
///
/// One method, so an in-process listener can be a lambda. What it receives is the same
/// {@link com.dataciders.qlive.model.push.Topic} message a browser would have got, the ids of the matching
/// subscriptions included; there is no second, tidier shape for the in-process case, because two shapes
/// would be two fan-out paths to keep in agreement.
@FunctionalInterface
public interface Recipient
{
    /// Delivers one message. Called on whatever thread published, with no lock of the pub/sub core's held.
    ///
    /// Implementations do not throw: a recipient whose delivery fails is its own problem to log and
    /// survive, and a publisher has no way to act on someone else's broken socket.
    void send(ServerMessage message);
}
