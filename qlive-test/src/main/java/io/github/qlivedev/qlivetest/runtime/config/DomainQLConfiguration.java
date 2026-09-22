package io.github.qlivedev.qlivetest.runtime.config;

import io.github.qlivedev.runtime.meta.QueryConfigMetadataProvider;
import io.github.qlivedev.qlivetest.domain.Public;
import io.github.qlivedev.qlivetest.domain.tables.pojos.Bar;
import io.github.qlivedev.qlivetest.domain.tables.pojos.Baz;
import io.github.qlivedev.qlivetest.domain.tables.pojos.Foo;
import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.config.SourceField;
import io.github.qlivedev.graphql.config.TargetField;
import io.github.qlivedev.graphql.meta.MetadataProvider;
import io.github.qlivedev.runtime.domain.QLiveDefaultDomain;
import io.github.qlivedev.runtime.meta.MergeMetadataProvider;
import io.github.qlivedev.runtime.query.DefaultQueryDocumentService;
import io.github.qlivedev.runtime.query.QueryDocumentService;
import graphql.GraphQL;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Collection;

import static io.github.qlivedev.qlivetest.domain.Tables.*;

/**
 * Exemplary configuration of GraphQL in a project.
 */
@Configuration
public class DomainQLConfiguration
{
    private final static Logger log = LoggerFactory.getLogger(DomainQLConfiguration.class);


    private final ApplicationContext applicationContext;
    private final DSLContext dslContext;


    @Autowired
    public DomainQLConfiguration(
        ApplicationContext applicationContext,
        DSLContext dslContext
    )
    {
        this.applicationContext = applicationContext;
        this.dslContext = dslContext;
    }

    
    /**
     * The application's own metadata provider. Declared rather than found by a component scan; what picks it
     * up is {@link #domainQL()} asking the context for every {@link MetadataProvider}, which is the same
     * route a module's provider takes.
     */
    @Bean
    public MetadataProvider exampleMetadataProvider()
    {
        return new ExampleMetadataProvider();
    }


    @Bean
    public QLiveDomain domainQL() throws IOException
    {
        return newDomainQL(
            dslContext,
            applicationContext.getBeansWithAnnotation(GraphQLLogic.class).values(),
            applicationContext.getBeansOfType(MetadataProvider.class).values()
        );
    }


    /**
     * Defines the application's domain, kept apart from the bean wiring above so that a test can build the same
     * schema without a database: nothing here touches the DSLContext until a query executes, so passing null is
     * enough to get at the schema and its meta data. The logic beans and metadata providers are what the schema
     * is built out of, so a test has to hand over the same ones to get the same schema.
     */
    static QLiveDomain newDomainQL(
        DSLContext dslContext,
        Collection<Object> logicBeans,
        Collection<MetadataProvider> metadataProviders
    ) throws IOException
    {
        final QLiveDomain domainQL = QLiveDefaultDomain.newDomain(dslContext, metadataProviders)
            //.parameterProvider(new AutomatonConnectionProviderFactory(applicationContext))
            .logicBeans(logicBeans)

            .objectTypes(Public.PUBLIC)

//            .withAdditionalInputTypes(
//                Node.class,
//                Bar.class,
//                ValidationRules.class,
//                QuxMain.class,
//                QuxA.class,
//                QuxB.class,
//                QuxC.class,
//
//                Baz.class,
//                BazValue.class,
//                BazLink.class,
//
//                Corge.class,
//                CorgeAssoc.class,
//                CorgeLink.class,
//                CorgeType.class,
//
//                Grault.class,
//                Garply.class,
//                MetaConfig.class,
//                Waldo.class
//            )
            
            // configure object creation for schema relationships
            .configureRelation(FOO.OWNER_ID, SourceField.OBJECT_AND_SCALAR, TargetField.MANY)
            .configureRelation(FOO.TYPE, SourceField.OBJECT_AND_SCALAR, TargetField.NONE, "fooType", null)
            .configureRelation(BAR_LINK.BAR_ID, SourceField.OBJECT_AND_SCALAR, TargetField.MANY, "bar", "bazLinks")
            .configureRelation(BAR_LINK.BAZ_ID, SourceField.OBJECT_AND_SCALAR, TargetField.MANY, "baz", "bazLinks")
            .configureNameField("name")

            .withMetadataProviders(

                // The house rule for every row type the domain has a query document for. Small on purpose:
                // it makes paging visible in the example views. A view wanting something else for a local
                // reason says so at its own useInjection() call rather than here.
                QueryConfigMetadataProvider.newProvider().
                    forAllTypes()
                        .pageSize(5)
                    .build(),

                MergeMetadataProvider.newProvider()

                    // Resolve conflicts for the Bar edit example
                    .resolveConflicts(Bar.class)
                    // many-to-many connected to Bar
                    .resolveConflicts(Baz.class)

                    // Set when the row is written and never again, so no two users can hold different opinions about
                    // it and there is nothing to gain from spending a mask bit on it.
                    .ignoreFields(Foo.class, "created")
            )

            /*
                documentation for the types defined in the QLive library
             */
            .withTypeDocsFrom(
                new ClassPathResource("qlive-typedocs.json").getInputStream()
            )
            /*
                handwritten JSON docs for example
             */
            .withTypeDocsFrom(
                new ClassPathResource("domain-typedocs.json").getInputStream()
            )
            /*
                local source docs (autogenerated by the maven 'update-typedocs' execution)
             */
            .withTypeDocsFrom(
                new ClassPathResource("source-typedocs.json").getInputStream()
            )


            .build();

        return domainQL;
    }


    @Bean
    public QueryDocumentService queryDocumentService(
        QLiveDomain domainQL,
        GraphQL graphQL) throws Exception
    {
        return new DefaultQueryDocumentService(
            domainQL,
            dslContext,
            graphQL
        );
    }

}
