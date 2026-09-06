package com.dataciders.qlive.runtime.config;

import com.dataciders.qlive.runtime.service.BootstrapService;
import com.dataciders.qlive.runtime.service.DefaultBootstrapService;
import com.dataciders.qlive.runtime.service.StaticAnalysisProvider;
import de.quinscape.domainql.DomainQL;
import com.dataciders.qlive.model.condition.ConditionParser;
import graphql.GraphQL;
import jakarta.servlet.ServletContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PreDestroy;
import java.io.IOException;

@Configuration
public class QLiveConfiguration
{
    private final static Logger log = LoggerFactory.getLogger(QLiveConfiguration.class);

    private final ServletContext servletContext;

    public QLiveConfiguration(ServletContext servletContext)
    {
        this.servletContext = servletContext;
    }
    
    /**
     * <p>
     *     The static analysis provider is required, not optional. Without one the bootstrap service could not
     *     tell which paths declare noSchema(), and a `null` from a provider has to keep meaning the one thing
     *     it means -- "the data is not there yet, ask again" -- rather than doubling as "this application
     *     never configured one". An application that forgets the bean gets a missing-bean failure at startup
     *     instead of pages that answer 503 forever.
     * </p>
     */
    @Bean
    public BootstrapService bootstrapService(DomainQL domainQL, StaticAnalysisProvider staticAnalysis)
        throws IOException
    {
        return new DefaultBootstrapService(servletContext, domainQL, staticAnalysis);
    }


    @Bean
    public GraphQL graphQL(DomainQL domainQL)
    {
        return GraphQL.newGraphQL(domainQL.getGraphQLSchema()).build();
    }


    @Bean
    public ConditionParser conditionParser()
    {
        return new ConditionParser();
    }
    
}
