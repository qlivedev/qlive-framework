package com.dataciders.qlive.runtime.config;

import com.dataciders.qlive.runtime.merge.DefaultFieldLayoutService;
import com.dataciders.qlive.runtime.merge.DefaultMergeService;
import com.dataciders.qlive.runtime.merge.DefaultVersionHolder;
import com.dataciders.qlive.runtime.merge.DefaultVersionService;
import com.dataciders.qlive.runtime.merge.FieldLayoutService;
import com.dataciders.qlive.runtime.merge.MergeLogic;
import com.dataciders.qlive.runtime.merge.MergeService;
import com.dataciders.qlive.runtime.merge.VersionCleanup;
import com.dataciders.qlive.runtime.merge.VersionHolder;
import com.dataciders.qlive.runtime.merge.VersionService;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.core.annotation.Order;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.util.List;

/// Every bean the framework contributes, declared.
///
/// Nothing here is found by a component scan, and nothing can be: an application's scan covers the
/// application's packages, so a `@Component` of the framework's is a `@Component` nobody looks at.
///
/// `@EnableScheduling` is here for the one task the framework runs, {@link VersionCleanup}. An application
/// that schedules nothing of its own needs no annotation of its own, and one that does is unaffected --
/// the annotation is idempotent.
@Configuration
@EnableScheduling
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
    public MergeService mergeService(
        DomainQL domainQL,
        DSLContext dslContext,
        FieldLayoutService fieldLayoutService,
        VersionService versionService
    )
    {
        return new DefaultMergeService(domainQL, dslContext, fieldLayoutService, versionService);
    }


    /// The field layouts masks are written against, and the startup check that no versioned type has more
    /// fields than a mask has bits.
    @Bean
    public FieldLayoutService fieldLayoutService(DomainQL domainQL, DSLContext dslContext)
    {
        return new DefaultFieldLayoutService(domainQL, dslContext);
    }


    /// The version records: written by the merge, read back by the chain walk.
    @Bean
    public VersionService versionService(
        DSLContext dslContext,
        VersionHolder versionHolder,
        ApplicationEventPublisher eventPublisher
    )
    {
        return new DefaultVersionService(dslContext, versionHolder, eventPublisher);
    }


    /// The in-memory half of the chain walk, and the first listener for the merge's event. A push module
    /// would be the second, listening for the same event and reading the same records.
    @Bean
    public VersionHolder versionHolder()
    {
        return new DefaultVersionHolder();
    }


    /// Drops what has expired. Without it `app_version` grows for as long as the application runs.
    ///
    /// The lifetime spans a weekend on purpose: Friday evening to Monday morning is 72 hours, and parking a
    /// change set across exactly that gap is a feature rather than an edge case.
    @Bean
    public VersionCleanup versionCleanup(
        VersionService versionService,
        VersionHolder versionHolder,
        FieldLayoutService fieldLayoutService,
        @Value("${qlive.merge.versionLifetime:P7D}") Duration versionLifetime
    )
    {
        return new VersionCleanup(versionService, versionHolder, fieldLayoutService, versionLifetime);
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
