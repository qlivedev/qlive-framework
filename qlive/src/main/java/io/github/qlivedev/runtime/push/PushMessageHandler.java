package io.github.qlivedev.runtime.push;

import io.github.qlivedev.model.push.ClientMessage;

import java.util.Set;

/// One feature's share of the push connection: the client message kinds it takes, and what it does with
/// them.
///
/// The socket carries whatever QLive needs to say, and pub/sub is one thing it says rather than the
/// protocol itself. This is where that stops being a claim about the message model and becomes true of the
/// transport: {@link PushWebSocketHandler} reads a frame and routes it, and knows nothing about channels,
/// subscriptions or conditions. Pub/sub contributes one of these; a feature that wants the connection
/// without wanting a channel -- presence, a connection-scoped notice -- contributes another beside it and
/// needs no change here.
///
/// A handler takes several kinds rather than one, because a feature's kinds belong together: the state
/// they share is the feature's, and splitting them across classes to satisfy the registry would mean
/// threading that state back through a constructor for nothing.
public interface PushMessageHandler
{
    /// The message kinds this handler takes, as the exact classes they arrive as.
    ///
    /// Claimed at wiring time, not asked per frame, so two handlers claiming the same kind is a startup
    /// failure rather than whichever one the iteration order reached first.
    Set<Class<? extends ClientMessage>> handles();


    /// Handles one frame, already parsed and known to be one of {@link #handles}.
    ///
    /// Throwing is how a handler refuses: {@link PushWebSocketHandler} answers the sender with an
    /// {@link io.github.qlivedev.model.push.Error}, addressed as precisely as the message allowed.
    ///
    /// @param recipient   the connection the frame came from, and where anything it is owed goes
    /// @param message     the frame
    void handle(Recipient recipient, ClientMessage message);
}
