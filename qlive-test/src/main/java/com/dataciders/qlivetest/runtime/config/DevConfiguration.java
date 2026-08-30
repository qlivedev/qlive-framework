package com.dataciders.qlivetest.runtime.config;

import de.quinscape.domainql.DomainQL;
import com.dataciders.qlive.runtime.controller.GraphQLController;
import com.dataciders.qlive.runtime.controller.TrackUsageDevController;
import com.dataciders.qlive.runtime.domain.GraphQLQueryTypingService;
import com.dataciders.qlive.model.ts.TrackUsageData;
import de.quinscape.spring.jsview.loader.JSONResourceConverter;
import de.quinscape.spring.jsview.loader.ResourceHandle;
import de.quinscape.spring.jsview.loader.ResourceLoader;
import graphql.GraphQL;
import jakarta.servlet.ServletContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.File;
import java.io.IOException;

@Configuration
public class DevConfiguration
{
    private final static Logger log = LoggerFactory.getLogger(DevConfiguration.class);

    private final ResourceLoader resourceLoader;

    public DevConfiguration(ResourceLoader resourceLoader)
    {
        this.resourceLoader = resourceLoader;
    }

    public final static String TRACK_USAGE = "/static/track-usage.json";

    @Bean
    @Profile("dev")
    public GraphQLQueryTypingService hotReloadTypingService(DomainQL domainQL, ServletContext servletContext) throws IOException
    {
        File tsSourcePath = new File(new File(servletContext.getRealPath("/")), "../../src/main/ts").getCanonicalFile();

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


    @Profile("prod")
    @Bean
    public ResourceHandle<TrackUsageData> prodStaticFunctionReferencesResourceHandle()
    {
        return resourceLoader.getResourceHandle(
            TRACK_USAGE,
            new JSONResourceConverter<>(TrackUsageData.class)
        );
    }

    @Bean
    public GraphQLController graphQLController(GraphQL graphQL)
    {
        return new GraphQLController(graphQL);
    }

}
