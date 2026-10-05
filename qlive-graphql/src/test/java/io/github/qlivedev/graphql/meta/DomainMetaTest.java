package io.github.qlivedev.graphql.meta;

import io.github.qlivedev.graphql.QLiveDomainBuilder;
import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.graphql.QLiveDomainTypeException;
import io.github.qlivedev.graphql.RelationBuilder;
import io.github.qlivedev.graphql.config.RelationModel;
import io.github.qlivedev.graphql.config.SourceField;
import io.github.qlivedev.graphql.config.TargetField;
import io.github.qlivedev.graphql.scalar.BigDecimalScalar;
import io.github.qlivedev.graphql.logicimpl.ConfigureNonDBByNameLogic;
import io.github.qlivedev.graphql.logicimpl.DecimalResponseLogic;
import io.github.qlivedev.graphql.logicimpl.OutputTypeOverrideByParamLogic;
import io.github.qlivedev.graphql.logicimpl.SizedResponseLogic;
import io.github.qlivedev.graphql.logicimpl.TestLogic;
import io.github.qlivedev.graphql.testdomain.Public;
import io.github.qlivedev.graphql.testdomain.tables.pojos.Bar;
import io.github.qlivedev.graphql.testdomain.tables.pojos.BarOrg;
import io.github.qlivedev.graphql.testdomain.tables.pojos.BarOwner;
import io.github.qlivedev.graphql.testdomain.tables.pojos.Foo;
import io.github.qlivedev.graphql.testdomain.tables.pojos.TargetNine;
import io.github.qlivedev.graphql.testdomain.tables.pojos.TargetNineCounts;
import graphql.schema.GraphQLSchema;
import io.github.qlivedev.util.JSONUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.github.qlivedev.graphql.testdomain.Tables.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

public class DomainMetaTest
{
    private final static Logger log = LoggerFactory.getLogger(DomainMetaTest.class);


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


            .withMetadataProviders(
                NameFieldProvider.newProvider()
                    .forAllTypes()
                        .nameFields("name")
                        .build()
            )

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

            .withMetadataProviders(
                NameFieldProvider.newProvider()
                    .forTypes(BarOwner.class, BarOrg.class, Foo.class)
                        .nameFields("name")
                    .andForType(Bar.class)
                        .nameFields("name", "description")
                        .build()
            )
            .build();


        final DomainTypeMeta barMeta = domain.getMetaData().getTypeMeta("Bar");
        final DomainTypeMeta barOwnerMeta = domain.getMetaData().getTypeMeta("BarOwner");
        final DomainTypeMeta barOrgMeta = domain.getMetaData().getTypeMeta("BarOrg");
        final DomainTypeMeta fooMeta = domain.getMetaData().getTypeMeta("Foo");

        assertThat( barMeta.getMeta(DomainMeta.NAME_FIELDS), is(Arrays.asList("name", "description")) );
        assertThat( barOwnerMeta.getMeta(DomainMeta.NAME_FIELDS), is(Collections.singletonList("name")) );
        assertThat( barOrgMeta.getMeta(DomainMeta.NAME_FIELDS), is(Collections.singletonList("name")) );
        assertThat( fooMeta.getMeta(DomainMeta.NAME_FIELDS), is(Collections.singletonList("name")) );
    }

    @Test
    public void testNameFieldPathsRejected()
    {
        // a name field is the row's own: a related row's name is something a query might not select
        assertThrows(
            IllegalArgumentException.class,
            () -> NameFieldProvider.newProvider().forType(Bar.class).nameFields("name", "owner.name")
        );
    }

    @Test
    public void testNamingFieldsError()
    {
        assertThrows(QLiveDomainTypeException.class, () ->
            QLiveDomainBuilder.newDomain(null)
                .objectTypes(Public.PUBLIC)
                .withMetadataProviders(
                    NameFieldProvider.newProvider()
                        .forType(Bar.class)
                            .nameFields("name", "wrong")
                            .build()
                )
                .build()
        );
    }

    @Test
    public void testNameFieldOfRelationRejected()
    {
        assertThrows(QLiveDomainTypeException.class, () ->
            QLiveDomainBuilder.newDomain(null)
                .objectTypes(Public.PUBLIC)
                .configureRelation(BAR.OWNER_ID, SourceField.OBJECT_AND_SCALAR, TargetField.NONE)
                .withMetadataProviders(
                    NameFieldProvider.newProvider()
                        .forType(Bar.class)
                            .nameFields("owner")
                            .build()
                )
                .build()
        );
    }

    @Test
    public void testNameFieldsForAllTypes()
    {
        final QLiveDomain domain = QLiveDomainBuilder.newDomain(null)
            .objectTypes(Public.PUBLIC)

            .configureRelation(BAR.OWNER_ID, SourceField.OBJECT_AND_SCALAR, TargetField.NONE)
            .configureRelation(BAR_OWNER.ORG_ID, SourceField.OBJECT_AND_SCALAR, TargetField.NONE)

            .withMetadataProviders(
                NameFieldProvider.newProvider()
                    .forAllTypes()
                        .nameFields("name")
                    .andForType(Bar.class)
                        .nameFields("name", "description")
                        .build()
            )
            .build();

        final DomainTypeMeta barMeta = domain.getMetaData().getTypeMeta("Bar");
        final DomainTypeMeta barOwnerMeta = domain.getMetaData().getTypeMeta("BarOwner");
        final DomainTypeMeta barOrgMeta = domain.getMetaData().getTypeMeta("BarOrg");
        final DomainTypeMeta fooMeta = domain.getMetaData().getTypeMeta("Foo");

        assertThat( barMeta.getMeta(DomainMeta.NAME_FIELDS), is(Arrays.asList("name", "description")) );
        assertThat( barOwnerMeta.getMeta(DomainMeta.NAME_FIELDS), is(Collections.singletonList("name")) );
        assertThat( barOrgMeta.getMeta(DomainMeta.NAME_FIELDS), is(Collections.singletonList("name")) );
        assertThat( fooMeta.getMeta(DomainMeta.NAME_FIELDS), is(Collections.singletonList("name")) );

    }

    @Test
    public void testNameFieldsForAllTypesNonDB()
    {
        final QLiveDomain domain = QLiveDomainBuilder.newDomain(null)
            .logicBeans(new ConfigureNonDBByNameLogic())
            .objectTypes(Public.PUBLIC)

            .configureRelation(BAR.OWNER_ID, SourceField.OBJECT_AND_SCALAR, TargetField.NONE)
            .configureRelation(BAR_OWNER.ORG_ID, SourceField.OBJECT_AND_SCALAR, TargetField.NONE)

            .withMetadataProviders(
                NameFieldProvider.newProvider()
                    .forAllTypes()
                        .nameFields("name")
                    .andForType(Bar.class)
                        .nameFields("name", "description")
                        .build()
            )
            .build();


        final DomainTypeMeta fullResponseMeta = domain.getMetaData().getTypeMeta("FullResponse");

        assertThat( fullResponseMeta.getMeta(DomainMeta.NAME_FIELDS), is(Collections.singletonList("name")) );
    }


    @Test
    public void testNameFieldsForAllTypesSkipTypesWithoutThem()
    {
        final QLiveDomain domain = QLiveDomainBuilder.newDomain(null)
            .objectTypes(Public.PUBLIC)

            .configureRelation(BAR.OWNER_ID, SourceField.OBJECT_AND_SCALAR, TargetField.NONE)

            .withMetadataProviders(
                NameFieldProvider.newProvider()
                    .forAllTypes()
                        .nameFields("name", "description")
                        .build()
            )
            .build();

        // only Bar has a description
        assertThat(
            domain.getMetaData().getTypeMeta("Bar").getMeta(DomainMeta.NAME_FIELDS),
            is(Arrays.asList("name", "description"))
        );
        assertThat(domain.getMetaData().getTypeMeta("BarOwner").getMeta(DomainMeta.NAME_FIELDS), is(nullValue()));
        assertThat(domain.getMetaData().getTypeMeta("Foo").getMeta(DomainMeta.NAME_FIELDS), is(nullValue()));
    }

    @Test
    public void testNameFieldsDeclaredTwice()
    {
        final NameFieldTypeConfigurer configurer = NameFieldProvider.newProvider()
            .forTypes(Bar.class, Foo.class)
            .nameFields("name");

        assertThrows(IllegalStateException.class, () -> configurer.andForType(Foo.class));
    }

    @Test
    public void testNameFieldsOfUnknownType()
    {
        assertThrows(QLiveDomainTypeException.class, () ->
            QLiveDomainBuilder.newDomain(null)
                .objectTypes(Public.PUBLIC)
                .withMetadataProviders(
                    NameFieldProvider.newProvider()
                        .forType(String.class)
                            .nameFields("name")
                            .build()
                )
                .build()
        );
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

    @Test
    public void testUniqueKeys()
    {
        final QLiveDomain domain = QLiveDomainBuilder.newDomain(null)
            .objectTypes(Public.PUBLIC)
            .build();

        final List<UniqueKeyMeta> sevenKeys = domain.getMetaData().getTypeMeta("TargetSeven")
            .getMeta(DomainMeta.UNIQUE_KEYS);

        // primary key first
        assertThat(sevenKeys.size(), is(2));
        assertThat(sevenKeys.get(0).getName(), is("pk_target_seven"));
        assertThat(sevenKeys.get(0).getFields(), is(List.of("id")));
        assertThat(sevenKeys.get(0).isPrimary(), is(true));
        assertThat(sevenKeys.get(1).getName(), is("target_seven_name_key"));
        assertThat(sevenKeys.get(1).getFields(), is(List.of("name")));
        assertThat(sevenKeys.get(1).isPrimary(), is(false));
        assertThat(sevenKeys.get(1).isNullable(), is(false));

        // multi-column constraint, in constraint order
        final List<UniqueKeyMeta> eightKeys = domain.getMetaData().getTypeMeta("TargetEight")
            .getMeta(DomainMeta.UNIQUE_KEYS);

        assertThat(eightKeys.get(1).getFields(), is(List.of("name", "num")));

        // reaches the client as field names
        final String json = JSONUtil.DEFAULT_GENERATOR.forValue(domain.getMetaData());
        assertThat(json, containsString("\"uniqueKeys\":[{"));
        assertThat(json, containsString("\"fields\":[\"name\",\"num\"]"));
    }


    @Test
    public void testSizeOfGeneratedPojos()
    {
        final QLiveDomain domain = QLiveDomainBuilder.newDomain(null)
            .objectTypes(Public.PUBLIC)
            .build();

        final DomainTypeMeta barMeta = domain.getMetaData().getTypeMeta("Bar");

        // jOOQ writes @Size(max = n) from the column length, and never a minimum
        assertThat(barMeta.getFieldMeta("id", DomainMeta.MAX_LENGTH), is(36));
        assertThat(barMeta.getFieldMeta("name", DomainMeta.MAX_LENGTH), is(100));
        assertThat(barMeta.getFieldMeta("name", DomainMeta.MIN_LENGTH), is(nullValue()));

        final String json = JSONUtil.DEFAULT_GENERATOR.forValue(domain.getMetaData());
        assertThat(json, containsString("\"maxLength\":100"));
    }

    @Test
    public void testSizeOfHandWrittenTypes()
    {
        final QLiveDomain domain = QLiveDomainBuilder.newDomain(null)
            .logicBeans(new SizedResponseLogic())
            .objectTypes(Public.PUBLIC)
            .build();

        final DomainTypeMeta sizedMeta = domain.getMetaData().getTypeMeta("SizedResponse");

        assertThat(sizedMeta.getFieldMeta("code", DomainMeta.MIN_LENGTH), is(2));
        assertThat(sizedMeta.getFieldMeta("code", DomainMeta.MAX_LENGTH), is(8));

        // a bound left at its default says nothing
        assertThat(sizedMeta.getFieldMeta("note", DomainMeta.MIN_LENGTH), is(nullValue()));
        assertThat(sizedMeta.getFieldMeta("note", DomainMeta.MAX_LENGTH), is(nullValue()));
        assertThat(sizedMeta.getFieldMeta("plain", DomainMeta.MAX_LENGTH), is(nullValue()));
    }


    @Test
    public void testDecimalPrecisionAndScale()
    {
        final QLiveDomain domain = QLiveDomainBuilder.newDomain(null)
            .logicBeans(new DecimalResponseLogic())
            .objectTypes(Public.PUBLIC)
            .withAdditionalScalar(BigDecimal.class, BigDecimalScalar.newScalar())
            .build();

        final DomainTypeMeta decimalMeta = domain.getMetaData().getTypeMeta("DecimalResponse");

        assertThat(decimalMeta.getFieldMeta("amount", DomainMeta.PRECISION), is(30));
        assertThat(decimalMeta.getFieldMeta("amount", DomainMeta.SCALE), is(10));

        // numeric(10) holds integers, which is worth saying
        assertThat(decimalMeta.getFieldMeta("count", DomainMeta.PRECISION), is(10));
        assertThat(decimalMeta.getFieldMeta("count", DomainMeta.SCALE), is(0));

        // an unconstrained numeric says nothing, and a timestamp's precision is not a decimal's
        assertThat(decimalMeta.getFieldMeta("unbounded", DomainMeta.PRECISION), is(nullValue()));
        assertThat(decimalMeta.getFieldMeta("unbounded", DomainMeta.SCALE), is(nullValue()));
        assertThat(decimalMeta.getFieldMeta("created", DomainMeta.PRECISION), is(nullValue()));

        // a BigInteger has a precision, and a scale that could only ever be 0
        assertThat(decimalMeta.getFieldMeta("mask", DomainMeta.PRECISION), is(39));
        assertThat(decimalMeta.getFieldMeta("mask", DomainMeta.SCALE), is(nullValue()));
        assertThat(decimalMeta.getFieldMeta("unboundedInt", DomainMeta.PRECISION), is(nullValue()));

        final String json = JSONUtil.DEFAULT_GENERATOR.forValue(domain.getMetaData());
        assertThat(json, containsString("\"precision\":30"));
    }
}
