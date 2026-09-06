package com.dataciders.qlivetest.runtime.config;

import de.quinscape.domainql.DomainQL;
import com.dataciders.qlive.runtime.controller.GraphQLController;
import com.dataciders.qlive.runtime.controller.TrackUsageDevController;
import com.dataciders.qlive.runtime.domain.GraphQLQueryTypingService;
import graphql.GraphQL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.File;
import java.io.IOException;

@Configuration
public class DevConfiguration
{
    private final static Logger log = LoggerFactory.getLogger(DevConfiguration.class);

    /**
     * <p>
     *     Creates the service that writes generated result types back into the frontend's query modules.
     * </p>
     * <p>
     *     {@code qlive.dev.ts-source} has to name the same directory as the track-usage Vite plugin's
     *     {@code sourceRoot}, because the module paths in the pushed track-usage data are relative to it.
     *     A relative value is resolved against the working directory the backend runs in.
     * </p>
     */
    @Bean
    @Profile("dev")
    public GraphQLQueryTypingService hotReloadTypingService(
        DomainQL domainQL,
        @Value("${qlive.dev.ts-source:frontend/src}")
        String tsSource
    ) throws IOException
    {
        final File tsSourcePath = new File(tsSource).getCanonicalFile();

        // Fail here rather than once per push: a wrong directory otherwise only shows up as a recurring
        // "Error updating TrackUsageData" from the debounce timer thread.
        if (!tsSourcePath.isDirectory())
        {
            throw new IllegalStateException(
                "qlive.dev.ts-source '" + tsSource + "' resolves to " + tsSourcePath +
                    ", which is not a directory (working directory is " + new File("").getCanonicalPath() + ")"
            );
        }

        log.info("Generating query types into {}", tsSourcePath);

        return new GraphQLQueryTypingService(
            domainQL,
            tsSourcePath
        );
    }

    @Profile("dev")
    @Bean
    public TrackUsageDevController trackUsageDevController(GraphQLQueryTypingService graphQLQueryTypingService)
    {
        return new TrackUsageDevController(graphQLQueryTypingService);
    }

    @Bean
    public GraphQLController graphQLController(GraphQL graphQL)
    {
        return new GraphQLController(graphQL);
    }

}
