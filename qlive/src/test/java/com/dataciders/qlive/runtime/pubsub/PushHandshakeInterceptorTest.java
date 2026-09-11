package com.dataciders.qlive.runtime.pubsub;

import com.dataciders.qlive.runtime.auth.AppAuthentication;
import com.dataciders.qlive.runtime.auth.AppUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/// The identity half of the handshake, on its own: given an authenticated request, the connection carries
/// who made it.
///
/// The other half -- that the security filter chain runs against the handshake at all, so that there is an
/// authentication here to read -- is not something this can prove, and is asserted against a real server
/// and a real session in `qlive-test`.
class PushHandshakeInterceptorTest
{
    private final PushHandshakeInterceptor interceptor = new PushHandshakeInterceptor();


    @AfterEach
    void clearContext()
    {
        SecurityContextHolder.clearContext();
    }


    @Test
    void putsTheConnectingUserOnTheSession()
    {
        final AppUserDetails details = new AppUserDetails(
            Map.of("id", "u-1", "login", "kim", "roles", "ROLE_USER", "password", "irrelevant")
        );

        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities())
        );

        final Map<String, Object> attributes = handshake();

        assertThat(attributes.get(PushHandshakeInterceptor.LOGIN), is("kim"));
        assertThat(attributes.get(PushHandshakeInterceptor.USER_ID), is("u-1"));
    }


    /// No authentication is not a refusal. Whether an unauthenticated connection may be made at all is the
    /// application's security rules to decide, the same way they decide it for every other URI; this only
    /// records what those rules let through.
    @Test
    void recordsTheAnonymousIdentityWhenThereIsNoAuthentication()
    {
        final Map<String, Object> attributes = handshake();

        assertThat(attributes.get(PushHandshakeInterceptor.LOGIN), is(AppAuthentication.ANONYMOUS));
        assertThat(attributes.get(PushHandshakeInterceptor.USER_ID), is(AppAuthentication.ANONYMOUS_ID));
    }


    private Map<String, Object> handshake()
    {
        final Map<String, Object> attributes = new HashMap<>();

        assertThat(interceptor.beforeHandshake(null, null, null, attributes), is(true));

        return attributes;
    }
}
