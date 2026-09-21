package io.github.qlivedev.runtime.pubsub;

import io.github.qlivedev.runtime.push.Recipient;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/// One channel: the class its payloads have, and the subscriptions currently on it.
///
/// Package-private, and named for what the design calls it, which is also what the message class
/// {@link io.github.qlivedev.model.push.Topic} is named. The two never meet outside
/// {@link DefaultPubSubService}, and keeping this one out of sight is what stops anybody else having to
/// tell them apart.
///
/// Subscribing and unsubscribing take the lock; publishing takes nothing. The list is immutable and
/// replaced wholesale, so a fan-out iterates whatever snapshot it read while subscriptions come and go
/// around it -- a message arriving at the same moment as an unsubscribe is delivered or not, and either is
/// correct.
final class Topic
{
    private final String name;

    private final Class<?> payloadType;

    private volatile List<TopicRegistration> registrations = List.of();


    Topic(String name, Class<?> payloadType)
    {
        this.name = name;
        this.payloadType = payloadType;
    }


    String name()
    {
        return name;
    }


    Class<?> payloadType()
    {
        return payloadType;
    }


    /// The subscriptions as they stand. Immutable, and safe to iterate for as long as the caller likes.
    List<TopicRegistration> registrations()
    {
        return registrations;
    }


    /// Registers a subscription, replacing the one that connection already held under that id.
    ///
    /// Replacing rather than refusing, because a client re-subscribing under an id it is already using
    /// means the condition changed -- the rows on screen are no longer the ones it was registered for --
    /// and the alternative would have it unsubscribe first and receive nothing in between.
    synchronized void subscribe(TopicRegistration registration)
    {
        final List<TopicRegistration> next = new ArrayList<>(registrations.size() + 1);

        for (TopicRegistration existing : registrations)
        {
            if (!existing.is(registration.recipient(), registration.id()))
            {
                next.add(existing);
            }
        }

        next.add(registration);
        registrations = List.copyOf(next);
    }


    /// Drops one subscription.
    ///
    /// @return whether there was one to drop
    synchronized boolean unsubscribe(Recipient recipient, String id)
    {
        return drop(registration -> registration.is(recipient, id));
    }


    /// Drops every subscription of one recipient, which is what a closing connection needs.
    ///
    /// @return whether there was anything to drop
    synchronized boolean unsubscribeAll(Recipient recipient)
    {
        return drop(registration -> registration.recipient() == recipient);
    }


    private boolean drop(Predicate<TopicRegistration> doomed)
    {
        final List<TopicRegistration> next = new ArrayList<>(registrations.size());

        for (TopicRegistration existing : registrations)
        {
            if (!doomed.test(existing))
            {
                next.add(existing);
            }
        }

        if (next.size() == registrations.size())
        {
            return false;
        }

        registrations = List.copyOf(next);
        return true;
    }


    @Override
    public String toString()
    {
        return "channel '" + name + "' of " + (payloadType == null ? "?" : payloadType.getName())
            + ", " + registrations.size() + " subscription(s)";
    }
}
