package com.dataciders.qlivetest.runtime.config;

import com.dataciders.qlive.runtime.controller.GraphQLController;
import com.dataciders.qlive.runtime.controller.TrackUsageDevController;
import com.dataciders.qlive.runtime.service.DevStaticAnalysisProvider;
import com.dataciders.qlive.runtime.service.ProdStaticAnalysisProvider;
import graphql.GraphQL;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class DevConfiguration
{
    /**
     * Where the analysis pushed by the Vite dev server lands -- the bootstrap service asks it which
     * paths declared noSchema(), and which query a view injects.
     */
    @Profile("dev")
    @Bean
    public DevStaticAnalysisProvider devStaticAnalysisProvider()
    {
        return new DevStaticAnalysisProvider();
    }


    @Profile("dev")
    @Bean
    public TrackUsageDevController trackUsageDevController(DevStaticAnalysisProvider devStaticAnalysisProvider)
    {
        return new TrackUsageDevController(devStaticAnalysisProvider);
    }


    /**
     * The production counterpart of {@link #devStaticAnalysisProvider()}: same data, read from what
     * {@code vite build} wrote instead of from what the dev server pushed.
     */
    @Profile("prod")
    @Bean
    public ProdStaticAnalysisProvider prodStaticAnalysisProvider()
    {
        return new ProdStaticAnalysisProvider();
    }

    @Bean
    public GraphQLController graphQLController(GraphQL graphQL)
    {
        return new GraphQLController(graphQL);
    }

}
