package io.github.qlivedev.qlivetest.runtime.config;

import io.github.qlivedev.runtime.auth.AppUserDetailsService;
import io.github.qlivedev.runtime.auth.DefaultPersistentTokenRepository;
import io.github.qlivedev.runtime.QLivePaths;
import io.github.qlivedev.runtime.controller.GraphQLController;
import io.github.qlivedev.runtime.security.GraphQLSecurityErrorHandler;
import io.github.qlivedev.qlivetest.domain.tables.pojos.AppLogin;
import io.github.qlivedev.qlivetest.domain.tables.pojos.AppUser;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import java.util.Arrays;

@Configuration
public class SecurityConfiguration
{
    private final static String[] PUBLIC_URIS = new String[]
        {
            "/",
            "/app/assets/**",
            "/api/bootstrap",
            "/assets/**",
            "/index.jsp",
            "/static/**",
            "/index.jsp",
            "/error"
        };

    /// The profile QLive's development endpoints belong to. Outside it they are refused, see
    /// {@link QLivePaths#DEV_URIS}.
    private final static Profiles DEV_PROFILE = Profiles.of("dev");

    /**
     * Login page and form target. Reachable without authentication like {@link #PUBLIC_URIS}, but
     * deliberately not one of them: those are excluded from CSRF, and the login POST is exactly the
     * request that has to stay protected, so that a foreign page can't log a user in as someone else.
     */
    public final static String LOGIN_URI = "/login";

    /**
     * Where a successful login lands. The application's routes live below Vite's base, so this is the
     * one view every user is known to be allowed to see.
     */
    private final static String LOGIN_SUCCESS_URI = "/app/home";

    private final DSLContext dslContext;

    private final boolean devMode;


    public SecurityConfiguration(DSLContext dslContext, Environment environment)
    {
        this.dslContext = dslContext;
        // acceptsProfiles rather than a look at spring.profiles.active, so that the
        // spring.profiles.default this application runs on counts as well
        this.devMode = environment.acceptsProfiles(DEV_PROFILE);
    }


    @Bean
    public SecurityFilterChain filterChain(
        HttpSecurity http,                                                   
        @Value("${application.remember-me.key}")
        String rememberMeKey
    )
    {
        return http
            .headers(
                headers -> headers.disable()
            )
            .authorizeHttpRequests(
                auth ->
                {
                    auth.requestMatchers(PUBLIC_URIS).permitAll()
                        // spelled out here rather than left to formLogin's permitAll(), which appends its
                        // rules behind the "/**" one below and would therefore never be reached
                        .requestMatchers(LOGIN_URI).permitAll();

                    // Ahead of the "/**" rule, which would otherwise let any logged-in user at them. In dev
                    // they are open, which is what the Vite dev server needs; outside it, they are refused to
                    // everyone, admins included. The mappings exist in every profile -- Spring evaluates
                    // @Profile for bean definitions, not for the request mappings of a bean that exists --
                    // so this rule is the whole of what stops them being used.
                    if (devMode)
                    {
                        auth.requestMatchers(QLivePaths.DEV_URIS).permitAll();
                    }
                    else
                    {
                        auth.requestMatchers(QLivePaths.DEV_URIS).denyAll();
                    }
                    auth.requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/**").hasRole("USER");
                }
            )

            .csrf(
                csrf -> csrf.ignoringRequestMatchers(csrfExemptUris())
            )

            // Without a configured authentication mechanism, Spring Security answers every
            // unauthenticated request with a bare 403 instead of sending the user somewhere they can
            // do something about it.
            .formLogin(
                login ->
                    login.loginPage(LOGIN_URI)
                        .defaultSuccessUrl(LOGIN_SUCCESS_URI)
                        .failureUrl(LOGIN_URI + "?error")
            )

            // ... which is the right answer for a view, and the wrong one for the GraphQL endpoint: no
            // fetch() call can do anything with a login page. That one URL answers in the format its
            // caller parses instead, both when it is not authenticated and when it is denied.
            //
            // Worth knowing, because spring security decides this and not us: form login registers its
            // redirect for requests that accept text/html, and the entry point registered first -- this
            // one -- is what answers everything matching neither. So an unauthenticated fetch() of
            // another URL gets this 401 rather than a login page it could not use either, which is the
            // more useful of the two even where the GraphQL shape of the body has nothing to say.
            .exceptionHandling(
                exceptions ->
                {
                    final GraphQLSecurityErrorHandler graphQLErrors = new GraphQLSecurityErrorHandler();
                    final PathPatternRequestMatcher graphQLEndpoint =
                        PathPatternRequestMatcher.withDefaults().matcher(GraphQLController.GRAPHQL_URI);

                    exceptions
                        .defaultAuthenticationEntryPointFor(graphQLErrors, graphQLEndpoint)
                        .defaultAccessDeniedHandlerFor(graphQLErrors, graphQLEndpoint);
                }
            )

            .userDetailsService(userDetailsServiceBean())
            .rememberMe(
                cfg ->
                    cfg.tokenRepository(persistentTokenRepository())
                        .userDetailsService(userDetailsServiceBean())
                        .key(rememberMeKey)
            )
            .build();
    }


    /**
     * The URIs no CSRF token is demanded for. QLive's development endpoints are among them only where they
     * can be reached at all -- a pattern that is refused anyway has no business weakening the rule that
     * would refuse it.
     */
    private String[] csrfExemptUris()
    {
        if (!devMode)
        {
            return PUBLIC_URIS;
        }

        final String[] exempt = Arrays.copyOf(PUBLIC_URIS, PUBLIC_URIS.length + 1);
        exempt[PUBLIC_URIS.length] = QLivePaths.DEV_URIS;
        return exempt;
    }


    @Bean
    public UserDetailsService userDetailsServiceBean()
    {
        return new AppUserDetailsService<>(dslContext, "app_user", AppUser.class);
    }


    @Bean
    public PersistentTokenRepository persistentTokenRepository()
    {
        return new DefaultPersistentTokenRepository<>(dslContext, "app_login", AppLogin.class);
    }

    @Bean
    public PasswordEncoder passwordEncoder()
    {
        return new BCryptPasswordEncoder();
    }
}
