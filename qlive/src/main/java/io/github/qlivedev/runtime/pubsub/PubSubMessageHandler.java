package io.github.qlivedev.runtime.pubsub;

import io.github.qlivedev.model.condition.CNode;
import io.github.qlivedev.model.push.ClientMessage;
import io.github.qlivedev.model.push.Publish;
import io.github.qlivedev.model.push.Subscribe;
import io.github.qlivedev.model.push.Subscribed;
import io.github.qlivedev.model.push.Unsubscribe;
import io.github.qlivedev.runtime.QLiveException;
import io.github.qlivedev.runtime.push.ConnectionListener;
import io.github.qlivedev.runtime.push.PushMessageHandler;
import io.github.qlivedev.runtime.push.Recipient;
import io.github.qlivedev.runtime.scalar.ConditionCoercing;
import io.github.qlivedev.graphql.QLiveDomain;
import graphql.GraphQLContext;

import java.util.Locale;
import java.util.Set;

/// Pub/sub's share of the push connection: the three kinds a client sends about channels, and the sweep a
/// closing connection needs.
///
/// One handler among however many the application wires, which is the shape the message model always
/// claimed and the transport now has. Everything channel-shaped is here -- the service, the condition
/// coercion, the `QLiveDomain` that coercion needs -- and none of it is in
/// {@link io.github.qlivedev.runtime.push.PushWebSocketHandler}.
public class PubSubMessageHandler
    implements PushMessageHandler, ConnectionListener
{
    private final PubSubService pubSub;

    /// Reads the values of a condition that arrived over the socket as the types their nodes name.
    ///
    /// Held here and not in the service, because this is where "off the wire" is: a condition Svenson built
    /// out of a frame carries whatever JSON had, while one an application hands to `subscribe()` in process
    /// is already made of Java objects and has nothing to re-read.
    private final ConditionCoercing coercing = new ConditionCoercing();


    public PubSubMessageHandler(PubSubService pubSub, QLiveDomain domainQL)
    {
        this.pubSub = pubSub;
        this.coercing.setDomain(domainQL);
    }


    @Override
    public Set<Class<? extends ClientMessage>> handles()
    {
        return Set.of(Subscribe.class, Unsubscribe.class, Publish.class);
    }


    @Override
    public void handle(Recipient recipient, ClientMessage message)
    {
        switch (message)
        {
            case Subscribe subscribe ->
            {
                final CNode condition = coercing.coerceValues(
                    subscribe.getCondition(),
                    GraphQLContext.getDefault(),
                    Locale.ROOT
                );

                pubSub.subscribe(recipient, subscribe.getTopic(), condition, subscribe.getId());
                recipient.send(subscribed(subscribe));
            }

            case Unsubscribe unsubscribe ->
                pubSub.unsubscribe(recipient, unsubscribe.getTopic(), unsubscribe.getId());

            // Parsed, routed and answered, but refused: what a client may publish, and to which channels,
            // is not settled. Until it is, the only way a message reaches a channel is a server-side
            // publish() call, and there is no path by which one client's frame can look to another like
            // something the framework said.
            //
            // The feature that looks like it needs this -- presence, telling everyone who has a row open --
            // wants its own message kind beside these rather than a channel it publishes onto: the server
            // has to own that state, stamp the identity from the connection instead of trusting what the
            // frame carried, and sweep it on disconnect. None of that is what a pass-through publish does.
            case Publish publish -> throw new QLiveException(
                "Publishing to '" + publish.getTopic() + "' from a client is not allowed"
            );

            default -> throw new QLiveException("Unhandled message kind: " + message.getType());
        }
    }


    /// Closing a connection sweeps every subscription it holds on every channel -- not the channels it is
    /// known to have subscribed to, all of them, because the bookkeeping that would make the shorter list
    /// is the bookkeeping that gets lost in a refactor.
    @Override
    public void closed(Recipient recipient)
    {
        pubSub.unsubscribeAll(recipient);
    }


    private static Subscribed subscribed(Subscribe subscribe)
    {
        final Subscribed ack = new Subscribed();
        ack.setTopic(subscribe.getTopic());
        ack.setId(subscribe.getId());
        return ack;
    }
}
