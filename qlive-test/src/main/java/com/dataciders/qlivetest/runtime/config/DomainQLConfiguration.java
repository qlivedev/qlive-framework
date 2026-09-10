package com.dataciders.qlivetest.runtime.config;

import com.dataciders.qlivetest.domain.Public;
import com.dataciders.qlivetest.domain.tables.pojos.Bar;
import com.dataciders.qlivetest.domain.tables.pojos.Baz;
import com.dataciders.qlivetest.domain.tables.pojos.Foo;
import de.quinscape.domainql.DomainQL;
import de.quinscape.domainql.annotation.GraphQLLogic;
import de.quinscape.domainql.config.SourceField;
import de.quinscape.domainql.config.TargetField;
import de.quinscape.domainql.meta.MetadataProvider;
import com.dataciders.qlive.runtime.domain.QLiveDomain;
import com.dataciders.qlive.runtime.meta.MergeMetadataProvider;
import com.dataciders.qlive.runtime.query.DefaultQueryDocumentService;
import com.dataciders.qlive.runtime.query.QueryDocumentService;
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

import static com.dataciders.qlivetest.domain.Tables.*;

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


    /**
     * What the application declares about merging its types. A second MetadataProvider bean next to the one
     * above, picked up the same way -- nothing about merging is wired anywhere else.
     * <p>
     * Which types take part is deliberately not in here: bar, baz, bar_link and foo have a version column
     * and therefore take part, qux and foo_type do not. What is declared is only what the framework cannot
     * work out, and an application declaring none of it still gets conflict detection on every versioned
     * type.
     */
    @Bean
    public MetadataProvider mergeMetadata()
    {
        return newMergeMetadata();
    }


    /**
     * The declarations themselves, kept apart from the bean wiring for the same reason {@link #newDomainQL}
     * is: a test builds the application's schema without a context and has to hand over the same providers
     * to get the same meta data.
     */
    static MetadataProvider newMergeMetadata()
    {
        return MergeMetadataProvider.newProvider()

            // The two sides of the many-to-many, which is what an edit view here works on: a clash on one of
            // those comes back to the form with both values rather than failing the save.
            .resolveConflicts(Bar.class)
            .resolveConflicts(Baz.class)

            // Set when the row is written and never again, so no two users can hold different opinions about
            // it and there is nothing to gain from spending a mask bit on it.
            .ignoreFields(Foo.class, "created");
    }


    @Bean
    public DomainQL domainQL() throws IOException
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
    static DomainQL newDomainQL(
        DSLContext dslContext,
        Collection<Object> logicBeans,
        Collection<MetadataProvider> metadataProviders
    ) throws IOException
    {
        final DomainQL domainQL = QLiveDomain.newDomain(dslContext, metadataProviders)
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

            /*
                documentation for the types defined in the QLive library
             */
            .withTypeDocsFrom(
                new ClassPathResource("qlive-typedocs.json").getInputStream()
            )
            /*
                hand-written JSON docs for example
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
        DomainQL domainQL,
        GraphQL graphQL) throws Exception
    {
        return new DefaultQueryDocumentService(
            domainQL,
            dslContext,
            graphQL
        );
    }

}
