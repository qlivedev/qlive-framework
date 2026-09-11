package com.dataciders.qlive.runtime.pubsub;

import com.dataciders.qlive.runtime.auth.AppAuthentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/// Reads who is connecting, at the moment they connect, and puts it on the websocket session.
///
/// Resolved fresh on every handshake, including every reconnect, and never minted in advance. The
/// handshake is an ordinary same-origin GET carrying the same session cookie as every other request, and
/// the security filter chain runs against it, so {@link AppAuthentication#current()} here is the same
/// identity the rest of the request path sees. There is no connection token to issue, spend, or run out
/// of -- and therefore no reconnect that fails because the identity it was carrying was used up.
public class PushHandshakeInterceptor
    implements HandshakeInterceptor
{
    private final static Logger log = LoggerFactory.getLogger(PushHandshakeInterceptor.class);

    /// Attribute the connecting user's `app_user.id` is stashed under. The same id
    /// `EntityVersion.ownerId` holds, which is what lets a subscriber tell somebody else's write from its
    /// own.
    public final static String USER_ID = "qlive.userId";

    /// Attribute the connecting user's login name is stashed under.
    public final static String LOGIN = "qlive.login";


    @Override
    public boolean beforeHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler handler,
        Map<String, Object> attributes
    )
    {
        final AppAuthentication auth = AppAuthentication.current();

        attributes.put(USER_ID, auth.getId());
        attributes.put(LOGIN, auth.getLogin());

        log.debug("Push handshake for {}", auth.getLogin());

        return true;
    }


    @Override
    public void afterHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler handler,
        Exception exception
    )
    {
    }
}
