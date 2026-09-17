package com.dataciders.qlive.runtime.pubsub;

import com.dataciders.qlive.model.condition.CNode;
import com.dataciders.qlive.model.push.TopicTypes;
import com.dataciders.qlive.runtime.QLiveException;
import com.dataciders.qlive.runtime.push.Recipient;

/// QLive's pub/sub: named, typed channels, a server-side condition per subscription, and fan-out to
/// whoever is listening.
///
/// General infrastructure, not an entity-version mechanism. Pushing a row's new version is the first thing
/// built on it and has a channel like any other; an application telling its own users that a job finished
/// or a document was countersigned registers its own channel beside it and needs nothing from the
/// framework it does not already have here.
///
/// A channel is a name bound to a Java class, and that binding is everything a channel is. It decides what
/// a subscriber's field paths are checked against, and it is what {@link TopicTypes} answers -- the one
/// thing reading an inbound payload needs from this registry, which is why this interface extends it
/// rather than making the transport hold two things.
///
/// In-memory and single-instance, like the merge's own version cache, and for the same reason: the
/// framework's scale is tens to hundreds of people doing internal work, and there is no clustering problem
/// here to solve yet.
public interface PubSubService
    extends TopicTypes
{
    /// Registers a channel, or confirms the one already registered.
    ///
    /// Required, and the only way a channel comes into being: neither publishing nor subscribing creates
    /// one. What channels exist is the server's to say, because the server is the half of the system that
    /// knows what they carry -- and a name that was never registered is a typo far more often than it is
    /// an intention, whichever side produced it. Publishing to an unknown channel is therefore a failure
    /// and not a quiet no-op, in-process callers included: a publisher that has misspelled its own channel
    /// is shouting into a room that does not exist, and should be told.
    ///
    /// Where an application does this is startup, next to whatever publishes on the channel. Doing it
    /// there is also what lets a client subscribe before the first message, and a client that cannot
    /// subscribe until somebody publishes is a client that misses the message it was waiting for.
    ///
    /// @param topic         channel name, which is what a client names in a `Subscribe`
    /// @param payloadType   class this channel's payloads have
    ///
    /// @throws QLiveException   if the channel is already bound to a different class
    void register(String topic, Class<?> payloadType);


    /// Registers one subscription.
    ///
    /// The condition is compiled here, once, against the channel's payload class, and everything that can
    /// be decided from it alone is decided here: a field path naming no property of that class, an
    /// operator this backend cannot evaluate, an operator given the wrong number of operands. A
    /// subscription that gets registered is one that will be evaluated, which is what lets whoever asked
    /// for it be told either way.
    ///
    /// Re-subscribing under an id the recipient already holds on this channel replaces that subscription,
    /// rather than adding a second one: a client changing what is on screen changes its condition, and
    /// having to unsubscribe first would leave a gap nothing is delivered in.
    ///
    /// @param recipient   where matching messages go
    /// @param topic       channel to subscribe to
    /// @param condition   condition a message is evaluated against, or `null` for everything the channel
    ///                    carries
    /// @param id          the recipient's own id for this subscription, which comes back on every message
    ///                    it matches
    ///
    /// @throws QLiveException   if no such channel is registered, or the condition cannot be compiled
    ///                          against its payload class
    void subscribe(Recipient recipient, String topic, CNode condition, String id);


    /// Drops one subscription. Dropping one that is not there is not an error -- it has the outcome that
    /// was asked for.
    void unsubscribe(Recipient recipient, String topic, String id);


    /// Drops every subscription a recipient holds, across every channel. What a closing connection needs,
    /// and the whole of what it needs.
    void unsubscribeAll(Recipient recipient);


    /// How many subscriptions a channel currently holds, across every recipient.
    ///
    /// For looking at a running system -- a health endpoint, an admin view, a test asserting that a closed
    /// connection took its subscriptions with it. Nothing in the fan-out reads it.
    ///
    /// @return the count, or 0 for a channel that does not exist
    int subscriptionCount(String topic);


    /// Sends a payload to everyone on a channel whose condition it matches.
    ///
    /// Publishing to a *registered* channel nobody has subscribed to is a no-op, deliberately: a publisher
    /// has no reason to know whether anyone is listening yet, and making it find out would make every
    /// publisher carry a case it cannot do anything about. A channel that was never registered is the
    /// other thing entirely, and fails -- see {@link #register}.
    ///
    /// @param topic     channel to publish on
    /// @param payload   what to publish, an instance of the class the channel is bound to
    ///
    /// @throws QLiveException   if no such channel is registered, or the payload is not of its class
    void publish(String topic, Object payload);
}
