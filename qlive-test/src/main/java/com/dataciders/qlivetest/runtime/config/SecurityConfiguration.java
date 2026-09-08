package com.dataciders.qlivetest.runtime.config;

import com.dataciders.qlive.runtime.auth.AppUserDetailsService;
import com.dataciders.qlive.runtime.auth.DefaultPersistentTokenRepository;
import com.dataciders.qlive.runtime.controller.GraphQLController;
import com.dataciders.qlive.runtime.security.GraphQLSecurityErrorHandler;
import com.dataciders.qlivetest.domain.tables.pojos.AppLogin;
import com.dataciders.qlivetest.domain.tables.pojos.AppUser;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

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
            "/error",
            "/_dev/**"
        };

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


    public SecurityConfiguration(DSLContext dslContext)
    {
        this.dslContext = dslContext;
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
                    auth.requestMatchers(PUBLIC_URIS).permitAll()
                        // spelled out here rather than left to formLogin's permitAll(), which appends its
                        // rules behind the "/**" one below and would therefore never be reached
                        .requestMatchers(LOGIN_URI).permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/**").hasRole("USER")
            )

            .csrf(
                csrf -> csrf.ignoringRequestMatchers(PUBLIC_URIS)
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
