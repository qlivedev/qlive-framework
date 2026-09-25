package io.github.qlivedev.graphql.meta;

import io.github.qlivedev.graphql.QLiveDomainBuilder;
import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.graphql.QLiveDomainTypeException;
import io.github.qlivedev.graphql.RelationBuilder;
import io.github.qlivedev.graphql.config.RelationModel;
import io.github.qlivedev.graphql.config.SourceField;
import io.github.qlivedev.graphql.config.TargetField;
import io.github.qlivedev.graphql.logicimpl.ConfigureNonDBByNameLogic;
import io.github.qlivedev.graphql.logicimpl.OutputTypeOverrideByParamLogic;
import io.github.qlivedev.graphql.logicimpl.TestLogic;
import io.github.qlivedev.graphql.testdomain.Public;
import io.github.qlivedev.graphql.testdomain.tables.pojos.Bar;
import io.github.qlivedev.graphql.testdomain.tables.pojos.BarOrg;
import io.github.qlivedev.graphql.testdomain.tables.pojos.BarOwner;
import io.github.qlivedev.graphql.testdomain.tables.pojos.Foo;
import io.github.qlivedev.graphql.testdomain.tables.pojos.TargetNine;
import io.github.qlivedev.graphql.testdomain.tables.pojos.TargetNineCounts;
import graphql.schema.GraphQLSchema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.github.qlivedev.graphql.testdomain.Tables.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

public class DomainQLMetaTest
{
    private final static Logger log = LoggerFactory.getLogger(DomainQLMetaTest.class);


    @Test
    public void testMetadataGeneration()
    {
        // checks that output type overriding works via @GraphQLTypeParam, too
        final QLiveDomain domain = QLiveDomainBuilder.newDomain(null)
            .objectTypes(Public.PUBLIC)

            .withRelation(
                new RelationBuilder()
                    .withForeignKeyFields(SOURCE_SIX.TARGET_ID)
                    .withTargetField(TargetField.MANY)
                    .withRightSideObjectName("manyObj")
            )


            .configureNameField("name")

            .logicBeans(Collections.singleton(new OutputTypeOverrideByParamLogic()))
            .build();
        final GraphQLSchema schema = domain.getGraphQLSchema();

        final List<RelationModel> relations = (List<RelationModel>) domain.getMetaData().getData().get("relations");

        assertThat(relations.size(), is(1));

        final RelationModel relationModel = relations.get(0);

        log.info("{}", relationModel);

        //log.info(new SchemaPrinter().print(domain.getGraphQLSchema()));

    }

    @Test
    public void testNameFields()
    {
        final QLiveDomain domain = QLiveDomainBuilder.newDomain(null)
            .objectTypes(Public.PUBLIC)

            .configureRelation(BAR.OWNER_ID, SourceField.OBJECT_AND_SCALAR, TargetField.NONE)
            .configureRelation(BAR_OWNER.ORG_ID, SourceField.OBJECT_AND_SCALAR, TargetField.NONE)

            .configureNameFieldForTypes("name", BarOwner.class, BarOrg.class, Foo.class)
            .configureNameFields(Bar.class,"name", "owner.name", "owner.org.name")
            .build();


        final DomainTypeMeta barMeta = domain.getMetaData().getTypeMeta("Bar");
        final DomainTypeMeta barOwnerMeta = domain.getMetaData().getTypeMeta("BarOwner");
        final DomainTypeMeta barOrgMeta = domain.getMetaData().getTypeMeta("BarOrg");
        final DomainTypeMeta fooMeta = domain.getMetaData().getTypeMeta("Foo");

        assertThat( barMeta.getMeta(DomainMeta.NAME_FIELDS), is(Arrays.asList("name", "owner.name", "owner.org.name")) );
        assertThat( barOwnerMeta.getMeta(DomainMeta.NAME_FIELDS), is(Collections.singletonList("name")) );
        assertThat( barOrgMeta.getMeta(DomainMeta.NAME_FIELDS), is(Collections.singletonList("name")) );
        assertThat( fooMeta.getMeta(DomainMeta.NAME_FIELDS), is(Collections.singletonList("name")) );
    }

    @Test
    public void testNamingFieldsManyToMany()
    {
        assertThrows(QLiveDomainTypeException.class, () -> {
                final QLiveDomain domain = QLiveDomainBuilder.newDomain(null)
                    .objectTypes(Public.PUBLIC)

                    .configureRelation(BAR.OWNER_ID, SourceField.OBJECT_AND_SCALAR, TargetField.MANY)
                    .configureRelation(BAR_OWNER.ORG_ID, SourceField.OBJECT_AND_SCALAR, TargetField.NONE)

                    // this makes sense from a GraphQL logic point of view, but we don't want the complications
                    // and the use-case for this is weak at best
                    .configureNameFields(BarOwner.class,"name", "bars.name")
                    .build();


    
        });
    }

    @Test
    public void testNamingFieldsError()
    {
        assertThrows(QLiveDomainTypeException.class, () -> {
                final QLiveDomain domain = QLiveDomainBuilder.newDomain(null)
                    .objectTypes(Public.PUBLIC)

                    .configureRelation(BAR.OWNER_ID, SourceField.OBJECT_AND_SCALAR, TargetField.NONE)
                    .configureRelation(BAR_OWNER.ORG_ID, SourceField.OBJECT_AND_SCALAR, TargetField.NONE)

                    .configureNameFields(Bar.class,"name", "wrong.name")
                    .build();
    
        });
    }

    @Test
    public void testNameFieldConfiguringByName()
    {
        final QLiveDomain domain = QLiveDomainBuilder.newDomain(null)
            .objectTypes(Public.PUBLIC)

            .configureRelation(BAR.OWNER_ID, SourceField.OBJECT_AND_SCALAR, TargetField.NONE)
            .configureRelation(BAR_OWNER.ORG_ID, SourceField.OBJECT_AND_SCALAR, TargetField.NONE)

            .configureNameField("name")
            .configureNameFields(Bar.class,"name", "owner.name", "owner.org.name")
            .build();

        final DomainTypeMeta barMeta = domain.getMetaData().getTypeMeta("Bar");
        final DomainTypeMeta barOwnerMeta = domain.getMetaData().getTypeMeta("BarOwner");
        final DomainTypeMeta barOrgMeta = domain.getMetaData().getTypeMeta("BarOrg");
        final DomainTypeMeta fooMeta = domain.getMetaData().getTypeMeta("Foo");

        assertThat( barMeta.getMeta(DomainMeta.NAME_FIELDS), is(Arrays.asList("name", "owner.name", "owner.org.name")) );
        assertThat( barOwnerMeta.getMeta(DomainMeta.NAME_FIELDS), is(Collections.singletonList("name")) );
        assertThat( barOrgMeta.getMeta(DomainMeta.NAME_FIELDS), is(Collections.singletonList("name")) );
        assertThat( fooMeta.getMeta(DomainMeta.NAME_FIELDS), is(Collections.singletonList("name")) );

    }

    @Test
    public void testNameFieldConfiguringNonDBByName()
    {
        final QLiveDomain domain = QLiveDomainBuilder.newDomain(null)
            .logicBeans(new ConfigureNonDBByNameLogic())
            .objectTypes(Public.PUBLIC)

            .configureRelation(BAR.OWNER_ID, SourceField.OBJECT_AND_SCALAR, TargetField.NONE)
            .configureRelation(BAR_OWNER.ORG_ID, SourceField.OBJECT_AND_SCALAR, TargetField.NONE)

            .configureNameField("name")
            .configureNameFields(Bar.class,"name", "owner.name", "owner.org.name")
            .build();


        final DomainTypeMeta fullResponseMeta = domain.getMetaData().getTypeMeta("FullResponse");

        assertThat( fullResponseMeta.getMeta(DomainMeta.NAME_FIELDS), is(Collections.singletonList("name")) );
    }


    @Test
    public void testRelationModelMetadata()
    {
        final QLiveDomain domain = QLiveDomainBuilder.newDomain(null)
            .objectTypes(Public.PUBLIC)
            .logicBeans(Collections.singleton(new TestLogic()))

            // source variants
            .withRelation(
                new RelationBuilder()
                    // trigger renaming in second
                    .withId("SourceTwo-target")
                    .withForeignKeyFields(SOURCE_ONE.TARGET_ID)
                    .withSourceField(SourceField.NONE)
                    .withTargetField(TargetField.NONE)
            )

            .withRelation(
                new RelationBuilder()
                    .withForeignKeyFields(SOURCE_TWO.TARGET_ID)
                    .withSourceField(SourceField.SCALAR)
                    .withTargetField(TargetField.NONE)
            )

            .withRelation(
                new RelationBuilder()
                    .withForeignKeyFields(SOURCE_THREE.TARGET_ID)
                    .withSourceField(SourceField.OBJECT)
                    .withTargetField(TargetField.NONE)
            )

            .withRelation(
                new RelationBuilder()
                    .withForeignKeyFields(SOURCE_FIVE.TARGET_ID)
                    .withSourceField(SourceField.NONE)
                    .withTargetField(TargetField.ONE)
            )

            .withRelation(
                new RelationBuilder()
                    .withForeignKeyFields(SOURCE_SIX.TARGET_ID)
                    .withSourceField(SourceField.NONE)
                    .withTargetField(TargetField.MANY)
            )

            .withRelation(
                new RelationBuilder()
                    .withId("SourceSeven-renamedTarget")
                    .withForeignKeyFields(SOURCE_SEVEN.TARGET)
                    .withSourceField(SourceField.OBJECT)
                    .withTargetField(TargetField.NONE)
                    .withLeftSideObjectName("targetObj")
            )

            .withRelation(
                new RelationBuilder()
                    .withForeignKeyFields(
                        SOURCE_EIGHT.TARGET_NAME, SOURCE_EIGHT.TARGET_NUM
                    )
                    .withSourceField(SourceField.OBJECT)
                    .withTargetField(TargetField.MANY)
                    .withLeftSideObjectName("targetEight")
                    .withRightSideObjectName("sourceEights")
            )
            .withRelation(
                new RelationBuilder()
                    .withPojoFields(
                        TargetNineCounts.class,
                        Collections.singletonList("targetId"),
                        TargetNine.class,
                        Collections.singletonList("id")
                    )
                    .withSourceField(SourceField.OBJECT)
                    .withTargetField(TargetField.ONE)
            )
            .build();

        final List<RelationModel> relationModels = (List<RelationModel>) domain.getMetaData().getData().get("relations");


        assertThat(relationModels.get(0).getId(), is("SourceTwo-target"));
        assertThat(relationModels.get(1).getId(), is("SourceTwo-target2"));
        assertThat(relationModels.get(5).getId(), is("SourceSeven-renamedTarget"));
    }
}
