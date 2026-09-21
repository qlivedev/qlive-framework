package io.github.qlivedev.model.push;

import jakarta.validation.constraints.NotNull;
import org.svenson.JSONProperty;

/// Abstract base class for every message QLive sends over its websocket connection, in either direction.
///
/// The set of message kinds is fixed at compile time and each kind is one subclass, dispatched by the same
/// class-name discriminator {@link io.github.qlivedev.model.condition.CNode} uses. Pub/sub is one of
/// those kinds, not the protocol itself: whatever QLive needs to say over the socket next gets a class
/// beside these rather than a second protocol.
///
/// A message travels in one direction only, which is what {@link ClientMessage} and {@link ServerMessage}
/// say. {@link PushMessageParser} rejects a message read against the wrong one.
public abstract class PushMessage
{
    /// The discriminator the JSON carries, derived from the class name. Read-only in both directions of
    /// the word: there is no field behind it, and a parser must not try to set it -- it is what told the
    /// parser which class to build in the first place.
    @JSONProperty(readOnly = true)
    @NotNull
    public String getType()
    {
        return this.getClass().getSimpleName();
    }
}
