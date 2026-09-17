package com.dataciders.qlive.runtime.pubsub;

import com.dataciders.qlive.runtime.push.Recipient;

import java.util.function.Predicate;

/// One subscription: who gets the messages, under which id, and which of them.
///
/// The condition is already compiled by the time one of these exists -- once, when the subscription was
/// registered -- so a publish evaluates a predicate and never looks at a `CNode` again. A `null` predicate
/// is a subscription to everything the channel carries, which is what a `Subscribe` with no condition
/// registers.
final class TopicRegistration
{
    private final Recipient recipient;

    private final String id;

    private final Predicate<Object> filter;


    TopicRegistration(Recipient recipient, String id, Predicate<Object> filter)
    {
        this.recipient = recipient;
        this.id = id;
        this.filter = filter;
    }


    Recipient recipient()
    {
        return recipient;
    }


    String id()
    {
        return id;
    }


    /// Whether this subscription wants the given payload.
    boolean wants(Object payload)
    {
        return filter == null || filter.test(payload);
    }


    /// Whether this is the same subscription as one being unsubscribed: the same connection's, under the
    /// same id. Recipients are compared by identity, because one connection is one object.
    boolean is(Recipient recipient, String id)
    {
        return this.recipient == recipient && this.id.equals(id);
    }


    @Override
    public String toString()
    {
        return "subscription '" + id + "' of " + recipient + (filter == null ? ", unfiltered" : "");
    }
}
