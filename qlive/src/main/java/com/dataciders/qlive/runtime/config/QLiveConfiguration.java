package com.dataciders.qlive.runtime.config;

import com.dataciders.qlive.runtime.merge.DefaultMergeService;
import com.dataciders.qlive.runtime.merge.MergeLogic;
import com.dataciders.qlive.runtime.merge.MergeService;
import com.dataciders.qlive.runtime.service.BootstrapService;
import com.dataciders.qlive.runtime.service.DefaultBootstrapService;
import com.dataciders.qlive.runtime.service.InjectionArgumentProcessor;
import com.dataciders.qlive.runtime.service.QueryConfigArgumentProcessor;
import com.dataciders.qlive.runtime.service.StaticAnalysisProvider;
import de.quinscape.domainql.DomainQL;
import com.dataciders.qlive.model.condition.ConditionParser;
import graphql.GraphQL;
import jakarta.servlet.ServletContext;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;

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
    public BootstrapService bootstrapService(
        DomainQL domainQL,
        GraphQL graphQL,
        StaticAnalysisProvider staticAnalysis,
        List<InjectionArgumentProcessor> argumentProcessors
    )
        throws IOException
    {
        return new DefaultBootstrapService(
            servletContext, domainQL, graphQL, staticAnalysis, argumentProcessors
        );
    }


    /// Completes the query configs an injection passes. Registered last, so that an application that wants
    /// query configs of its own understanding only has to say `@Order` on its own processor -- see
    /// {@link InjectionArgumentProcessor}, which is also where an application adds a processor for types the
    /// framework knows nothing about.
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public InjectionArgumentProcessor queryConfigArgumentProcessor(DomainQL domainQL)
    {
        return new QueryConfigArgumentProcessor(domainQL);
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


    /// The write side of the framework, writing the application's own JOOQ schema.
    ///
    /// An application that needs something else registers a {@link MergeService} bean of its own; nothing
    /// below reaches past the interface.
    @Bean
    public MergeService mergeService(DomainQL domainQL, DSLContext dslContext)
    {
        return new DefaultMergeService(domainQL, dslContext);
    }


    /// The framework's own GraphQL logic bean, and the reason every one of them is declared here rather than
    /// annotated and left to be found.
    ///
    /// `@GraphQLLogic` is meta-annotated `@Component`, which only means anything to a component scan -- and
    /// an application's scan covers the application's packages. Declared, this bean is picked up by the
    /// `getBeansWithAnnotation()` call the application's DomainQL configuration already makes, which is all
    /// it takes and the only thing that works.
    ///
    /// The merge service is injected lazily because the domain is built out of the logic beans: asking for
    /// the service here, eagerly, would ask for the {@link DomainQL} it needs while it is still being built.
    @Bean
    public MergeLogic mergeLogic(@Lazy MergeService mergeService)
    {
        return new MergeLogic(mergeService);
    }

}
