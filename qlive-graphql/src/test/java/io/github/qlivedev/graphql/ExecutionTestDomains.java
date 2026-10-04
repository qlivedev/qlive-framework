package io.github.qlivedev.graphql;

import io.github.qlivedev.graphql.beans.GenericScalarLogic;
import io.github.qlivedev.graphql.beans.SumPerMonth;
import io.github.qlivedev.graphql.config.SourceField;
import io.github.qlivedev.graphql.config.TargetField;
import io.github.qlivedev.graphql.generic.DomainObject;
import io.github.qlivedev.graphql.generic.DomainObjectScalar;
import io.github.qlivedev.graphql.generic.GenericScalar;
import io.github.qlivedev.graphql.generic.GenericScalarType;
import io.github.qlivedev.graphql.logicimpl.AccessDomainLogic;
import io.github.qlivedev.graphql.logicimpl.BigNumericLogic;
import io.github.qlivedev.graphql.logicimpl.BinaryDataLogic;
import io.github.qlivedev.graphql.logicimpl.CustomFetcherLogic;
import io.github.qlivedev.graphql.logicimpl.DegenerifiedContainerLogic;
import io.github.qlivedev.graphql.logicimpl.DegenerifiedInputLogic;
import io.github.qlivedev.graphql.logicimpl.DegenerifyAndRenameLogic;
import io.github.qlivedev.graphql.logicimpl.DegenerifyContainerLogic;
import io.github.qlivedev.graphql.logicimpl.DoubleDegenerificationLogic;
import io.github.qlivedev.graphql.logicimpl.FetcherContextLogic;
import io.github.qlivedev.graphql.logicimpl.GenericDomainLogic;
import io.github.qlivedev.graphql.logicimpl.GenericDomainOutputLogic;
import io.github.qlivedev.graphql.logicimpl.GetterArgLogic;
import io.github.qlivedev.graphql.logicimpl.ListInputLogic;
import io.github.qlivedev.graphql.logicimpl.LogicWithEnums;
import io.github.qlivedev.graphql.logicimpl.LogicWithEnums2;
import io.github.qlivedev.graphql.logicimpl.NullForComplexValueLogic;
import io.github.qlivedev.graphql.logicimpl.NullPropInDomainObjectLogic;
import io.github.qlivedev.graphql.logicimpl.SumPerMonthLogic;
import io.github.qlivedev.graphql.logicimpl.TestLogic;
import io.github.qlivedev.graphql.logicimpl.TypeConversionLogic;
import io.github.qlivedev.graphql.logicimpl.TypeParamLogic;
import io.github.qlivedev.graphql.logicimpl.TypeParamMutationLogic;
import io.github.qlivedev.graphql.scalar.BigDecimalScalar;
import io.github.qlivedev.graphql.scalar.BigIntegerScalar;
import io.github.qlivedev.graphql.testdomain.Public;
import io.github.qlivedev.graphql.testdomain.tables.pojos.Foo;
import io.github.qlivedev.graphql.testdomain.tables.pojos.TargetNine;
import io.github.qlivedev.graphql.testdomain.tables.pojos.TargetNineCounts;
import graphql.Scalars;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import org.jooq.DSLContext;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.github.qlivedev.graphql.testdomain.Tables.*;

/**
 * The domains {@link QLiveDomainExecutionTest} runs its queries against.
 * <p>
 * {@link ExecutionTestSchemaTest} merges all of them into the schema the IDE checks the test's queries against, so a
 * test domain belongs here and in {@link #all()}, not inline in the test.
 * </p>
 */
public final class ExecutionTestDomains
{
    private ExecutionTestDomains()
    {
    }


    /**
     * Every domain in this class, with {@code null} for the DSL context, which the schema does not depend on.
     */
    public static List<QLiveDomainBuilder> all()
    {
        return Arrays.asList(
            main(null),
            typeConversion(null),
            customFetcher(null),
            getterArgs(null),
            enums(null),
            degenerifyAndRename(),
            degenerifiedInput(),
            degenerifiedContainer(),
            degenerifyContainer(),
            doubleDegenerification(),
            genericDomainObject(),
            genericDomainObjectOutput(),
            typeParam(),
            typeParamMutation(),
            nullForComplexValue(),
            accessDomain(),
            genericScalar(),
            genericScalarWithTables(),
            fetcherContextForwardRef(null),
            fetcherContextToOne(null),
            fetcherContextToMany(null),
            dbView(),
            listInput(),
            nullPropInDomainObject(),
            binaryData(),
            bigNumeric()
        );
    }


    /**
     * The test domain tables with every source and target field variant, plus additional queries and mutations.
     */
    public static QLiveDomainBuilder main(DSLContext dslContext)
    {
        return QLiveDomainBuilder.newDomain(dslContext)
            .objectTypes(Public.PUBLIC)
            .logicBeans(Arrays.asList(new TestLogic(dslContext), new TypeConversionLogic()))

            // source variants
            .withRelation(
                new RelationBuilder()
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

            .additionalQueries(GraphQLFieldDefinition.newFieldDefinition()
                .name("extraQuery")
                .type(Scalars.GraphQLString)
                .argument(
                    GraphQLArgument.newArgument()
                        .name("value")
                        .type(Scalars.GraphQLString)
                        .build()
                )
                .dataFetcher(env -> "extra:" + env.getArgument("value"))
                .build()
            )

            .additionalMutations(GraphQLFieldDefinition.newFieldDefinition()
                .name("extraMutation")
                .type(Scalars.GraphQLString)
                .argument(
                    GraphQLArgument.newArgument()
                        .name("value")
                        .type(Scalars.GraphQLString)
                        .build()
                )
                .dataFetcher(env -> "mutated:" + env.getArgument("value"))
                .build()
            );
    }


    public static QLiveDomainBuilder typeConversion(DSLContext dslContext)
    {
        return QLiveDomainBuilder.newDomain(dslContext)
            .logicBeans(Collections.singletonList(new TypeConversionLogic()));
    }


    public static QLiveDomainBuilder customFetcher(DSLContext dslContext)
    {
        return QLiveDomainBuilder.newDomain(dslContext)
            .logicBeans(Collections.singletonList(new CustomFetcherLogic()));
    }


    public static QLiveDomainBuilder getterArgs(DSLContext dslContext)
    {
        return QLiveDomainBuilder.newDomain(dslContext)
            .logicBeans(Collections.singletonList(new GetterArgLogic()));
    }


    public static QLiveDomainBuilder enums(DSLContext dslContext)
    {
        return QLiveDomainBuilder.newDomain(dslContext)
            .logicBeans(Arrays.asList(new LogicWithEnums(), new LogicWithEnums2()));
    }


    public static QLiveDomainBuilder degenerifyAndRename()
    {
        return QLiveDomainBuilder.newDomain(null)
            .logicBeans(Collections.singleton(new DegenerifyAndRenameLogic()));
    }


    public static QLiveDomainBuilder degenerifiedInput()
    {
        return QLiveDomainBuilder.newDomain(null)
            .logicBeans(Collections.singleton(new DegenerifiedInputLogic()));
    }


    public static QLiveDomainBuilder degenerifiedContainer()
    {
        return QLiveDomainBuilder.newDomain(null)
            .logicBeans(Collections.singleton(new DegenerifiedContainerLogic()));
    }


    public static QLiveDomainBuilder degenerifyContainer()
    {
        return QLiveDomainBuilder.newDomain(null)
            .logicBeans(Collections.singleton(new DegenerifyContainerLogic()));
    }


    public static QLiveDomainBuilder doubleDegenerification()
    {
        return QLiveDomainBuilder.newDomain(null)
            .logicBeans(Collections.singleton(new DoubleDegenerificationLogic()));
    }


    public static QLiveDomainBuilder genericDomainObject()
    {
        return QLiveDomainBuilder.newDomain(null)
            .objectTypes(Public.PUBLIC)
            .logicBeans(Collections.singleton(new GenericDomainLogic()))
            .withAdditionalScalar(DomainObject.class, DomainObjectScalar.newDomainObjectScalar())
            .withAdditionalInputType(Foo.class);
    }


    public static QLiveDomainBuilder genericDomainObjectOutput()
    {
        return QLiveDomainBuilder.newDomain(null)
            .objectTypes(Public.PUBLIC)
            .withAdditionalScalar(DomainObject.class, DomainObjectScalar.newDomainObjectScalar())
            .logicBeans(Collections.singleton(new GenericDomainOutputLogic()));
    }


    public static QLiveDomainBuilder typeParam()
    {
        return QLiveDomainBuilder.newDomain(null)
            .objectTypes(Public.PUBLIC)
            .logicBeans(Collections.singleton(new TypeParamLogic()));
    }


    public static QLiveDomainBuilder typeParamMutation()
    {
        return QLiveDomainBuilder.newDomain(null)
            .objectTypes(Public.PUBLIC)
            .logicBeans(Collections.singleton(new TypeParamMutationLogic()));
    }


    public static QLiveDomainBuilder nullForComplexValue()
    {
        return QLiveDomainBuilder.newDomain(null)
            .objectTypes(Public.PUBLIC)
            .logicBeans(Collections.singleton(new NullForComplexValueLogic()));
    }


    public static QLiveDomainBuilder accessDomain()
    {
        return QLiveDomainBuilder.newDomain(null)
            .objectTypes(Public.PUBLIC)
            .logicBeans(Collections.singleton(new AccessDomainLogic()));
    }


    public static QLiveDomainBuilder genericScalar()
    {
        return QLiveDomainBuilder.newDomain(null)
            .logicBeans(Collections.singleton(new GenericScalarLogic()))
            .withAdditionalScalar(GenericScalar.class, GenericScalarType.newGenericScalar())
            .withAdditionalScalar(DomainObject.class, DomainObjectScalar.newDomainObjectScalar());
    }


    public static QLiveDomainBuilder genericScalarWithTables()
    {
        return genericScalar()
            .objectTypes(Public.PUBLIC);
    }


    public static QLiveDomainBuilder fetcherContextForwardRef(DSLContext dslContext)
    {
        return QLiveDomainBuilder.newDomain(dslContext)
            .objectTypes(Public.PUBLIC)
            .logicBeans(new FetcherContextLogic())
            .withRelation(
                new RelationBuilder()
                    .withForeignKeyFields(SOURCE_THREE.TARGET_ID)
                    .withSourceField(SourceField.OBJECT)
            );
    }


    public static QLiveDomainBuilder fetcherContextToOne(DSLContext dslContext)
    {
        return QLiveDomainBuilder.newDomain(dslContext)
            .objectTypes(Public.PUBLIC)
            .logicBeans(new FetcherContextLogic())
            .withRelation(
                new RelationBuilder()
                    .withForeignKeyFields(SOURCE_FIVE.TARGET_ID)
                    .withSourceField(SourceField.NONE)
                    .withTargetField(TargetField.ONE)
            );
    }


    public static QLiveDomainBuilder fetcherContextToMany(DSLContext dslContext)
    {
        return QLiveDomainBuilder.newDomain(dslContext)
            .objectTypes(Public.PUBLIC)
            .logicBeans(new FetcherContextLogic())
            .withRelation(
                new RelationBuilder()
                    .withForeignKeyFields(SOURCE_SIX.TARGET_ID)
                    .withSourceField(SourceField.NONE)
                    .withTargetField(TargetField.MANY)
            );
    }


    public static QLiveDomainBuilder dbView()
    {
        return QLiveDomainBuilder.newDomain(null)
            .objectTypes(Public.PUBLIC)
            .logicBeans(Collections.singleton(new SumPerMonthLogic()))
            .objectType(SumPerMonth.class);
    }


    public static QLiveDomainBuilder listInput()
    {
        return QLiveDomainBuilder.newDomain(null)
            .objectTypes(Public.PUBLIC)
            .logicBeans(Collections.singleton(new ListInputLogic()))
            .withAdditionalInputType(Foo.class)
            .withAdditionalScalar(DomainObject.class, DomainObjectScalar.newDomainObjectScalar());
    }


    public static QLiveDomainBuilder nullPropInDomainObject()
    {
        return QLiveDomainBuilder.newDomain(null)
            .objectTypes(Public.PUBLIC)
            .logicBeans(Collections.singleton(new NullPropInDomainObjectLogic()))
            .withAdditionalScalar(DomainObject.class, DomainObjectScalar.newDomainObjectScalar());
    }


    public static QLiveDomainBuilder binaryData()
    {
        return QLiveDomainBuilder.newDomain(null)
            .objectTypes(Public.PUBLIC)
            .logicBeans(Collections.singleton(new BinaryDataLogic()));
    }


    public static QLiveDomainBuilder bigNumeric()
    {
        return QLiveDomainBuilder.newDomain(null)
            .objectTypes(Public.PUBLIC)
            .logicBeans(Collections.singleton(new BigNumericLogic()))
            .withAdditionalScalar(BigDecimal.class, BigDecimalScalar.newScalar())
            .withAdditionalScalar(BigInteger.class, BigIntegerScalar.newScalar());
    }
}
