package io.github.qlivedev.runtime.config;

import io.github.qlivedev.runtime.merge.DefaultFieldLayoutService;
import io.github.qlivedev.runtime.merge.DefaultMergeService;
import io.github.qlivedev.runtime.merge.DefaultVersionHolder;
import io.github.qlivedev.runtime.merge.DefaultVersionService;
import io.github.qlivedev.runtime.merge.FieldLayoutService;
import io.github.qlivedev.runtime.merge.MergeLogic;
import io.github.qlivedev.runtime.merge.MergeService;
import io.github.qlivedev.runtime.merge.VersionCleanup;
import io.github.qlivedev.runtime.merge.VersionHolder;
import io.github.qlivedev.runtime.merge.VersionService;
import io.github.qlivedev.runtime.QLivePaths;
import io.github.qlivedev.runtime.pubsub.DefaultPubSubService;
import io.github.qlivedev.runtime.pubsub.EntityVersionPublisher;
import io.github.qlivedev.runtime.pubsub.PubSubMessageHandler;
import io.github.qlivedev.runtime.pubsub.PubSubService;
import io.github.qlivedev.runtime.push.ConnectionListener;
import io.github.qlivedev.runtime.push.PushHandshakeInterceptor;
import io.github.qlivedev.runtime.push.PushMessageHandler;
import io.github.qlivedev.runtime.push.PushWebSocketHandler;
import io.github.qlivedev.runtime.service.BootstrapService;
import io.github.qlivedev.runtime.service.DefaultBootstrapService;
import io.github.qlivedev.runtime.service.InjectionArgumentProcessor;
import io.github.qlivedev.runtime.service.QueryConfigArgumentProcessor;
import io.github.qlivedev.runtime.service.StaticAnalysisProvider;
import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.model.condition.ConditionParser;
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
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;

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
/// the annotation is idempotent. `@EnableWebSocket` is here for the same reason and on the same terms: the
/// push transport is the framework's, and an application that registers handlers of its own contributes
/// another {@link WebSocketConfigurer} beside this one.
@Configuration
@EnableScheduling
@EnableWebSocket
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
        QLiveDomain domain,
        GraphQL graphQL,
        StaticAnalysisProvider staticAnalysis,
        List<InjectionArgumentProcessor> argumentProcessors
    )
        throws IOException
    {
        return new DefaultBootstrapService(
            servletContext, domain, graphQL, staticAnalysis, argumentProcessors
        );
    }


    /// Completes the query configs an injection passes. Registered last, so that an application that wants
    /// query configs of its own understanding only has to say `@Order` on its own processor -- see
    /// {@link InjectionArgumentProcessor}, which is also where an application adds a processor for types the
    /// framework knows nothing about.
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public InjectionArgumentProcessor queryConfigArgumentProcessor(QLiveDomain domain)
    {
        return new QueryConfigArgumentProcessor(domain);
    }


    @Bean
    public GraphQL graphQL(QLiveDomain domain)
    {
        return GraphQL.newGraphQL(domain.getGraphQLSchema()).build();
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
        QLiveDomain domain,
        DSLContext dslContext,
        FieldLayoutService fieldLayoutService,
        VersionService versionService
    )
    {
        return new DefaultMergeService(domain, dslContext, fieldLayoutService, versionService);
    }


    /// The field layouts masks are written against, and the startup check that no versioned type has more
    /// fields than a mask has bits.
    @Bean
    public FieldLayoutService fieldLayoutService(QLiveDomain domain, DSLContext dslContext)
    {
        return new DefaultFieldLayoutService(domain, dslContext);
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
    /// `getBeansWithAnnotation()` call the application's QLiveDomain configuration already makes, which is all
    /// it takes and the only thing that works.
    ///
    /// The merge service is injected lazily because the domain is built out of the logic beans: asking for
    /// the service here, eagerly, would ask for the {@link QLiveDomain} it needs while it is still being built.
    @Bean
    public MergeLogic mergeLogic(@Lazy MergeService mergeService)
    {
        return new MergeLogic(mergeService);
    }


    /// Pub/sub itself: the channel registry, and the fan-out every publisher goes through.
    ///
    /// General infrastructure rather than an entity-version mechanism, which is why it is declared here on
    /// its own and not inside whatever first uses it. An application registers its own channels against
    /// this bean and publishes on them with nothing further to configure.
    @Bean
    public PubSubService pubSubService()
    {
        return new DefaultPubSubService();
    }


    /// Pub/sub's share of the push connection. One {@link PushMessageHandler} among however many an
    /// application wires, and the only one the framework itself contributes today.
    @Bean
    public PubSubMessageHandler pubSubMessageHandler(PubSubService pubSubService, QLiveDomain domain)
    {
        return new PubSubMessageHandler(pubSubService, domain);
    }


    /// The push transport, holding every handler the context declares.
    ///
    /// Collected by injection rather than named here, which is the whole point of the handler seam: an
    /// application adding a feature to the connection declares a `PushMessageHandler` bean and changes
    /// nothing else.
    @Bean
    public PushWebSocketHandler pushWebSocketHandler(
        List<PushMessageHandler> handlers,
        List<ConnectionListener> connectionListeners
    )
    {
        return new PushWebSocketHandler(handlers, connectionListeners);
    }


    /// Pub/sub's first consumer: a merge's version records, on the "EntityVersion" channel.
    ///
    /// Declared beside the service rather than inside it, because it is one channel among however many an
    /// application registers and gets nothing the others do not.
    @Bean
    public EntityVersionPublisher entityVersionPublisher(PubSubService pubSubService)
    {
        return new EntityVersionPublisher(pubSubService);
    }


    /// Maps the push endpoint and puts the connecting user's identity on the session.
    ///
    /// The handshake is left to the application's security rules like any other URI. It is a same-origin
    /// GET carrying the session cookie, the filter chain runs against it, and a catch-all rule of the kind
    /// every application has covers it without naming it.
    @Bean
    public WebSocketConfigurer pushWebSocketConfigurer(PushWebSocketHandler pushWebSocketHandler)
    {
        return registry ->
            registry.addHandler(pushWebSocketHandler, QLivePaths.PUSH_URI)
                .addInterceptors(new PushHandshakeInterceptor());
    }

}
