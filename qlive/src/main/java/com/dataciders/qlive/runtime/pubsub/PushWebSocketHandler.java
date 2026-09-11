package com.dataciders.qlive.runtime.pubsub;

import com.dataciders.qlive.model.push.ClientMessage;
import com.dataciders.qlive.model.push.Error;
import com.dataciders.qlive.model.push.Publish;
import com.dataciders.qlive.model.push.PushMessageParser;
import com.dataciders.qlive.model.push.Subscribe;
import com.dataciders.qlive.model.push.Subscribed;
import com.dataciders.qlive.model.push.Unsubscribe;
import com.dataciders.qlive.model.condition.CNode;
import com.dataciders.qlive.runtime.QLiveException;
import com.dataciders.qlive.runtime.scalar.ConditionCoercing;
import de.quinscape.domainql.DomainQL;
import graphql.GraphQLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Locale;

/// Serves {@link com.dataciders.qlive.runtime.QLivePaths#PUSH_URI}: reads a frame, does what it says, and
/// answers when there is something to answer.
///
/// A raw handler, not STOMP. Every message QLive sends over this connection is one of the classes in
/// {@link com.dataciders.qlive.model.push}, dispatched by its own `type` field, and a sub-protocol that
/// brings its own framing, its own subscription model and its own broker would be three things to map onto
/// the ones already here.
///
/// The connection is the unit of cleanup. One is one {@link Recipient} for as long as it lives, and
/// closing it sweeps every subscription it holds on every channel -- not the channels it is known to have
/// subscribed to, all of them, because the bookkeeping that would make the shorter list is the bookkeeping
/// that gets lost in a refactor.
public class PushWebSocketHandler
    extends TextWebSocketHandler
{
    private final static Logger log = LoggerFactory.getLogger(PushWebSocketHandler.class);

    /// Session attribute the connection's own {@link Recipient} lives in, so that there is no second map
    /// of live connections to keep in step with the container's.
    private final static String RECIPIENT = "qlive.recipient";

    private final PubSubService pubSub;

    private final PushMessageParser parser = new PushMessageParser();

    /// Reads the values of a condition that arrived over this socket as the types their nodes name.
    ///
    /// Held here and not in the service, because this is where "off the wire" is: a condition Svenson built
    /// out of a frame carries whatever JSON had, while one an application hands to `subscribe()` in process
    /// is already made of Java objects and has nothing to re-read.
    private final ConditionCoercing coercing = new ConditionCoercing();


    public PushWebSocketHandler(PubSubService pubSub, DomainQL domainQL)
    {
        this.pubSub = pubSub;
        this.coercing.setDomainQL(domainQL);
    }


    @Override
    public void afterConnectionEstablished(WebSocketSession session)
    {
        final Object login = session.getAttributes().get(PushHandshakeInterceptor.LOGIN);

        session.getAttributes().put(RECIPIENT, new WebSocketRecipient(session, String.valueOf(login)));

        log.debug("Push connection {} opened for {}", session.getId(), login);
    }


    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status)
    {
        final Recipient recipient = recipient(session);

        if (recipient != null)
        {
            pubSub.unsubscribeAll(recipient);
        }

        log.debug("Push connection {} closed: {}", session.getId(), status);
    }


    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage frame)
    {
        final Recipient recipient = recipient(session);
        ClientMessage message = null;

        try
        {
            message = parser.parseClientMessage(frame.getPayload());
            handle(recipient, message);
        }
        catch (RuntimeException e)
        {
            // Answered rather than only logged. A subscription that was refused is one the client is
            // waiting for messages from, and a server-side log line is somewhere it will never look.
            log.warn("Error handling a push frame from {}", recipient, e);
            recipient.send(error(message, e));
        }
    }


    private void handle(Recipient recipient, ClientMessage message)
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
            case Publish publish -> throw new QLiveException(
                "Publishing to '" + publish.getTopic() + "' from a client is not allowed"
            );

            case null -> throw new QLiveException("Empty push frame");

            default -> throw new QLiveException(
                "Unhandled message kind: " + message.getType()
            );
        }
    }


    private static Subscribed subscribed(Subscribe subscribe)
    {
        final Subscribed ack = new Subscribed();
        ack.setTopic(subscribe.getTopic());
        ack.setId(subscribe.getId());
        return ack;
    }


    /// The failure, addressed as precisely as the frame allowed. A frame that did not parse names no
    /// channel and no subscription, and saying so is more use than inventing one.
    private static Error error(ClientMessage message, RuntimeException cause)
    {
        final Error error = new Error();

        switch (message)
        {
            case Subscribe subscribe ->
            {
                error.setTopic(subscribe.getTopic());
                error.setId(subscribe.getId());
            }
            case Unsubscribe unsubscribe ->
            {
                error.setTopic(unsubscribe.getTopic());
                error.setId(unsubscribe.getId());
            }
            case Publish publish -> error.setTopic(publish.getTopic());
            case null, default ->
            {
            }
        }

        error.setMessage(cause.getMessage() == null ? cause.toString() : cause.getMessage());
        return error;
    }


    private static Recipient recipient(WebSocketSession session)
    {
        return (Recipient) session.getAttributes().get(RECIPIENT);
    }
}
