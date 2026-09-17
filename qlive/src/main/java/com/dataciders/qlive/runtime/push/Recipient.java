package com.dataciders.qlive.runtime.push;

import com.dataciders.qlive.model.push.ServerMessage;

/// Somewhere a message can be delivered to.
///
/// A websocket connection is one, and the only one the transport makes. A framework or application bean
/// that wants to hear what a feature has to say without a browser being involved is the other, and it is
/// the reason this is an interface rather than the connection itself -- an in-process listener registers
/// through the same call a connection does, and nothing behind it ever learns which it is talking to.
///
/// It takes a {@link ServerMessage}, not any one kind of them, which is what lets a feature other than
/// pub/sub deliver over the same connection: a notice addressed to one connection and belonging to no
/// channel is a `ServerMessage` like any other.
///
/// One method, so an in-process listener can be a lambda. What it receives is the same message a browser
/// would have got; there is no second, tidier shape for the in-process case, because two shapes would be
/// two delivery paths to keep in agreement.
@FunctionalInterface
public interface Recipient
{
    /// Delivers one message. Called on whatever thread sent it, with no lock of any feature's held.
    ///
    /// Implementations do not throw: a recipient whose delivery fails is its own problem to log and
    /// survive, and a sender has no way to act on someone else's broken socket.
    void send(ServerMessage message);
}
