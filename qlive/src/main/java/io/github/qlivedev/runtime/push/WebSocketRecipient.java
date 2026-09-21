package io.github.qlivedev.runtime.push;

import io.github.qlivedev.model.push.ServerMessage;
import io.github.qlivedev.util.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

/// One websocket connection, as anything delivering over it sees it.
///
/// Sends are serialized on this object because a `WebSocketSession` is not safe for two threads to write
/// to at once, and two threads delivering to one connection at once is ordinary. A slow
/// client therefore holds up whoever is sending, which is the price of the simple version and is
/// accepted at the scale the framework is built for.
///
/// A failed send is logged and swallowed. The connection is about to be closed by whatever broke it, and
/// {@link PushWebSocketHandler#afterConnectionClosed} tells every {@link ConnectionListener} then -- a
/// sender has nothing to do about a socket that is not its own.
final class WebSocketRecipient
    implements Recipient
{
    private final static Logger log = LoggerFactory.getLogger(WebSocketRecipient.class);

    private final WebSocketSession session;

    private final String login;


    WebSocketRecipient(WebSocketSession session, String login)
    {
        this.session = session;
        this.login = login;
    }


    @Override
    public synchronized void send(ServerMessage message)
    {
        if (!session.isOpen())
        {
            return;
        }

        try
        {
            session.sendMessage(new TextMessage(JSONUtil.DEFAULT_GENERATOR.forValue(message)));
        }
        catch (IOException | IllegalStateException e)
        {
            log.warn("Could not send {} to {}", message.getType(), this, e);
        }
    }


    @Override
    public String toString()
    {
        return "push connection " + session.getId() + " of " + login;
    }
}
