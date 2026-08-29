package com.dataciders.qlivetest.runtime.config;

import com.dataciders.qlivetest.domain.tables.pojos.AppLogin;
import com.dataciders.qlivetest.domain.tables.pojos.AppUser;
import com.dataciders.qlivetest.runtime.auth.AppUserDetailsService;
import com.dataciders.qlivetest.runtime.auth.DefaultPersistentTokenRepository;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

@Configuration
public class SecurityConfiguration
{
    private final static String[] PUBLIC_URIS = new String[]
        {
            "/",
            "/app/**",
            "/index.jsp",
            "/static/**",
            "/index.jsp",
            "/error",
            "/_dev/**"
        };

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
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/**").hasRole("USER")
            )

            .csrf(
                csrf -> csrf.ignoringRequestMatchers(PUBLIC_URIS)
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

}
