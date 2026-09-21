package io.github.qlivedev.runtime.push;

import io.github.qlivedev.model.push.Addressed;
import io.github.qlivedev.model.push.ClientMessage;
import io.github.qlivedev.model.push.DynamicPayload;
import io.github.qlivedev.model.push.Error;
import io.github.qlivedev.model.push.PushMessageParser;
import io.github.qlivedev.runtime.QLiveException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// Serves {@link io.github.qlivedev.runtime.QLivePaths#PUSH_URI}: reads a frame, hands it to whichever
/// {@link PushMessageHandler} claimed its kind, and answers the sender when it fails.
///
/// That is the whole of what it does. It holds no channel registry, compiles no condition and imports
/// nothing from any feature built on the connection -- pub/sub included, which reaches it as one handler
/// among however many. The message model calls itself general infrastructure; this is the layer where
/// that has to be true or the claim is decoration.
///
/// A raw handler, not STOMP. Every message QLive sends over this connection is one of the classes in
/// {@link io.github.qlivedev.model.push}, dispatched by its own `type` field, and a sub-protocol that
/// brings its own framing, its own subscription model and its own broker would be three things to map onto
/// the ones already here.
///
/// The connection is the unit of cleanup. One is one {@link Recipient} for as long as it lives, and what
/// closing it costs is each handler's to sweep, through whatever {@link ConnectionListener} it registers.
public class PushWebSocketHandler
    extends TextWebSocketHandler
{
    private final static Logger log = LoggerFactory.getLogger(PushWebSocketHandler.class);

    /// Session attribute the connection's own {@link Recipient} lives in, so that there is no second map
    /// of live connections to keep in step with the container's.
    private final static String RECIPIENT = "qlive.recipient";

    /// Which handler takes which kind, resolved once. A frame is a map lookup, not a walk.
    private final Map<Class<? extends ClientMessage>, PushMessageHandler> handlers;

    private final List<ConnectionListener> listeners;

    private final PushMessageParser parser = new PushMessageParser();


    /// @param handlers    the features sharing this connection
    /// @param listeners   whoever wants to know when one opens or closes
    ///
    /// @throws QLiveException   if two handlers claim the same message kind
    public PushWebSocketHandler(List<PushMessageHandler> handlers, List<ConnectionListener> listeners)
    {
        this.handlers = index(handlers);
        this.listeners = List.copyOf(listeners);
    }


    private static Map<Class<? extends ClientMessage>, PushMessageHandler> index(
        List<PushMessageHandler> handlers
    )
    {
        final Map<Class<? extends ClientMessage>, PushMessageHandler> index = new HashMap<>();

        for (PushMessageHandler handler : handlers)
        {
            for (Class<? extends ClientMessage> kind : handler.handles())
            {
                final PushMessageHandler previous = index.put(kind, handler);

                if (previous != null)
                {
                    // At wiring time, because the alternative is one of them quietly never running.
                    throw new QLiveException(
                        "Both " + previous.getClass().getName() + " and " + handler.getClass().getName() +
                            " claim " + kind.getSimpleName()
                    );
                }
            }
        }

        return Map.copyOf(index);
    }


    @Override
    public void afterConnectionEstablished(WebSocketSession session)
    {
        final Object login = session.getAttributes().get(PushHandshakeInterceptor.LOGIN);
        final Recipient recipient = new WebSocketRecipient(session, String.valueOf(login));

        session.getAttributes().put(RECIPIENT, recipient);

        for (ConnectionListener listener : listeners)
        {
            listener.opened(recipient);
        }

        log.debug("Push connection {} opened for {}", session.getId(), login);
    }


    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status)
    {
        final Recipient recipient = recipient(session);

        if (recipient != null)
        {
            closed(recipient);
        }

        log.debug("Push connection {} closed: {}", session.getId(), status);
    }


    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage frame)
    {
        onFrame(recipient(session), frame.getPayload());
    }


    /// One frame from one connection: parse it, route it, and answer the sender if either failed.
    ///
    /// Where the session stops being needed. Everything past finding the connection's {@link Recipient} is
    /// about the message and the recipient, and nothing here reaches back into Spring's plumbing.
    void onFrame(Recipient recipient, String json)
    {
        ClientMessage message = null;

        try
        {
            message = parser.parseClientMessage(json);
            handle(recipient, message);
        }
        catch (RuntimeException e)
        {
            // Answered rather than only logged. A request that was refused is one the client is waiting on,
            // and a server-side log line is somewhere it will never look.
            log.warn("Error handling a push frame from {}", recipient, e);
            recipient.send(error(message, e));
        }
    }


    /// Tells every listener the connection is gone, whatever the others made of it.
    void closed(Recipient recipient)
    {
        for (ConnectionListener listener : listeners)
        {
            try
            {
                listener.closed(recipient);
            }
            catch (RuntimeException e)
            {
                // One feature failing to clean up must not cost the others theirs.
                log.error("Error closing {} out of {}", recipient, listener.getClass().getName(), e);
            }
        }
    }


    private void handle(Recipient recipient, ClientMessage message)
    {
        if (message == null)
        {
            throw new QLiveException("Empty push frame");
        }

        final PushMessageHandler handler = handlers.get(message.getClass());

        if (handler == null)
        {
            // A kind the message model knows and this application wired nobody to take. Worth saying so
            // plainly: the frame was well-formed, and what is missing is a handler, not a field.
            throw new QLiveException("Nothing handles " + message.getType() + " on this connection");
        }

        handler.handle(recipient, message);
    }


    /// The failure, addressed as precisely as the frame allowed.
    ///
    /// Read off the model-layer interfaces rather than off the kinds themselves, so that a feature added
    /// beside pub/sub gets its errors addressed without this class learning its message classes. A frame
    /// that did not parse names nothing at all, and saying only what went wrong is more use than inventing
    /// a channel it might have meant.
    private static Error error(ClientMessage message, RuntimeException cause)
    {
        final Error error = new Error();

        switch (message)
        {
            case Addressed addressed ->
            {
                error.setTopic(addressed.getTopic());
                error.setId(addressed.getId());
            }
            case DynamicPayload payload -> error.setTopic(payload.getTopic());
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
