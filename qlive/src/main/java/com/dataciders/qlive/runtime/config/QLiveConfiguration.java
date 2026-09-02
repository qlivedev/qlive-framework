package com.dataciders.qlive.runtime.config;

import com.dataciders.qlive.runtime.service.BootstrapService;
import com.dataciders.qlive.runtime.service.StreamResourceLoader;
import de.quinscape.domainql.DomainQL;
import com.dataciders.qlive.model.condition.ConditionParser;
import de.quinscape.spring.jsview.loader.ResourceLoader;
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
    
    @Bean
    public BootstrapService bootstrapService(DomainQL domainQL) throws IOException
    {
        return new BootstrapService(servletContext, domainQL);
    }


    @Bean
    public ResourceLoader resourceLoader()
    {
        return new StreamResourceLoader(servletContext, "/");
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

    @PreDestroy
    public void destroy(ResourceLoader resourceLoader)
    {
        resourceLoader.shutDown();
    }

}
