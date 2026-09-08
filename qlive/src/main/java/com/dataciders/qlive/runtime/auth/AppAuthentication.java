package com.dataciders.qlive.runtime.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Limited, immutable view on the current user's {@link AppUserDetails} for client consumption.
 */
public final class AppAuthentication
{
    /**
     * Login name for the anonymous account.
     */
    public static final String ANONYMOUS = "anonymous";

    /**
     * Anonymous role.
     */
    public static final String ROLE_ANONYMOUS = "ROLE_ANONYMOUS";

    /** Make sure the anonymous DB-User has this magic id */
    public static final String ANONYMOUS_ID = "af432487-a1b1-4f99-96d4-3b8e9796c95a";

    /**
     * The one anonymous AppAuthentication instance
     */
    private static final AppAuthentication ANONYMOUS_AUTH;

    static {
        ANONYMOUS_AUTH = new AppAuthentication(
            ANONYMOUS,
            Collections.singleton(ROLE_ANONYMOUS),
            ANONYMOUS_ID
        );
    }

    private final String login;
    private final Set<String> roles;

    private final String id;


    private AppAuthentication(String login, Set<String> roles, String id)
    {
        if (login == null)
        {
            throw new IllegalArgumentException("login can't be null");
        }

        if (roles == null)
        {
            throw new IllegalArgumentException("roles can't be null");
        }

        if (id == null)
        {
            throw new IllegalArgumentException("id can't be null");
        }

        this.login = login;
        this.roles = roles;
        this.id = id;
    }

    /**
     * Accesses the spring security context to get the current {@link AppUserDetails}.
     * For anonymous users, {@link #ANONYMOUS_AUTH} is returned.
     *
     * @return the current authentication, never null
     */
    public static AppAuthentication current()
    {
        SecurityContext context = SecurityContextHolder.getContext();

        Authentication authentication = context.getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppUserDetails)
        {
            final AppUserDetails details = (AppUserDetails) authentication.getPrincipal();
            return new AppAuthentication(details.getUsername(), details.getRoles(), details.getId());
        }
        if (authentication != null && authentication.getPrincipal() instanceof AppAuthentication)
        {
            return (AppAuthentication) authentication.getPrincipal();
        }
        else
        {
            return ANONYMOUS_AUTH;
        }
    }


    public String getLogin()
    {
        return login;
    }


    public Set<String> getRoles()
    {
        return roles;
    }


    public String getId()
    {
        return id;
    }


    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o instanceof AppAuthentication)
        {
            AppAuthentication that = (AppAuthentication) o;
            return login.equals(that.login) &&
                roles.equals(that.roles) &&
                id.equals(that.id);
        }
        return false;
    }


    @Override
    public int hashCode()
    {
        return Objects.hash(login, roles, id);
    }


    @Override
    public String toString()
    {
        return super.toString() + ": "
            + "login = '" + login + '\''
            + ", roles = " + roles
            + ", id = '" + id + '\''
            ;
    }
}
