package io.github.qlivedev.qlive.runtime.auth;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

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
    public static final String ANONYMOUS_ID = "3f7c1a10-0000-4000-8000-000000000000";

    /**
     * The one anonymous AppAuthentication instance
     */
    private static final AppAuthentication ANONYMOUS_AUTH;

    /// Login name of the system account, which work nobody in particular started runs as: a scheduled job, a
    /// cleanup. Like anonymous, it is a row of the user table that nobody can log in as.
    public static final String SYSTEM = "system";

    /// System role.
    public static final String ROLE_SYSTEM = "ROLE_SYSTEM";

    /// The id the system user's row must have.
    public static final String SYSTEM_ID = "3f7c1a10-0000-4000-8000-777777777777";

    /// The identity of the system user, for {@link #runAs}.
    public static final AppAuthentication SYSTEM_AUTH;

    static {
        ANONYMOUS_AUTH = new AppAuthentication(
            ANONYMOUS,
            Collections.singleton(ROLE_ANONYMOUS),
            ANONYMOUS_ID
        );
        SYSTEM_AUTH = new AppAuthentication(
            SYSTEM,
            Collections.singleton(ROLE_SYSTEM),
            SYSTEM_ID
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


    /// Runs the given work as the given identity, which whatever reads the security context in it sees: the merge
    /// records its writes for it, authorization checks it. The context is restored afterwards.
    ///
    /// For work outside a request: a scheduled job runs as {@link #SYSTEM_AUTH}, a background import as the user
    /// who started it, whose identity {@link #current()} gave on the request.
    ///
    /// @param identity identity to run as
    /// @param work     work to run
    /// @param <T>      result type
    ///
    /// @return what the work returned
    public static <T> T runAs(AppAuthentication identity, Supplier<T> work)
    {
        final SecurityContext previous = SecurityContextHolder.getContext();

        final SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                identity,
                null,
                identity.getRoles().stream().map(SimpleGrantedAuthority::new).toList()
            )
        );
        SecurityContextHolder.setContext(context);
        try
        {
            return work.get();
        }
        finally
        {
            SecurityContextHolder.setContext(previous);
        }
    }


    /// Runs the given work as the given identity, see {@link #runAs(AppAuthentication, Supplier)}.
    ///
    /// @param identity identity to run as
    /// @param work     work to run
    public static void runAs(AppAuthentication identity, Runnable work)
    {
        runAs(identity, () -> {
            work.run();
            return null;
        });
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
