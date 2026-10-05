package io.github.qlivedev.runtime.domain;

import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLTypeUtil;
import io.github.qlivedev.graphql.ManyToManyBuilder;
import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.graphql.QLiveDomainBuilder;
import io.github.qlivedev.graphql.QLiveDomainBuilderException;
import io.github.qlivedev.testdomain.Public;
import io.github.qlivedev.testdomain.tables.pojos.TestBar;
import io.github.qlivedev.testdomain.tables.pojos.TestBarLink;
import io.github.qlivedev.testdomain.tables.pojos.TestBaz;
import io.github.qlivedev.util.JSONUtil;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.UnaryOperator;

import static io.github.qlivedev.testdomain.Tables.TEST_BAR_LINK;
import static io.github.qlivedev.testdomain.Tables.TEST_FOO;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// What declaring a many-to-many does to the schema and the meta data, and what it refuses at startup.
class ManyToManyDeclarationTest
{
    private final static String DECLARED =
        "[{\"left\":{\"field\":\"bazs\",\"linkField\":\"barId\",\"type\":\"TestBar\"}," +
            "\"linkType\":\"TestBarLink\"," +
            "\"right\":{\"field\":\"bars\",\"linkField\":\"bazId\",\"type\":\"TestBaz\"}," +
            "\"writable\":true}]";


    /// Each end gets a non-null list of the other end's type, next to the link arrays the ordinary relations
    /// over the same foreign keys still make.
    @Test
    void givesEachEndAListOfTheOther()
    {
        final QLiveDomain domain = TestDomainConfig.domainNoMeta();

        assertThat(GraphQLTypeUtil.simplePrint(field(domain, "TestBar", "bazs").getType()), is("[TestBaz]!"));
        assertThat(GraphQLTypeUtil.simplePrint(field(domain, "TestBaz", "bars").getType()), is("[TestBar]!"));
        assertThat(field(domain, "TestBar", "bazLinks"), is(notNullValue()));
    }


    /// The client reads the declaration from the meta data: the link type, each end's type, the link column
    /// pointing at it and its through field, and whether the fields can be written.
    @Test
    void shipsTheDeclarationInTheMetaData()
    {
        assertThat(manyToManyJSON(TestDomainConfig.domainNoMeta()), is(DECLARED));
    }


    /// A link table without foreign-key constraints is declared by its properties, and the declaration is the
    /// same one.
    @Test
    void declaresTheSameFromPojoFields()
    {
        final QLiveDomain domain = domain(
            b -> b.withManyToMany(
                new ManyToManyBuilder()
                    .withPojoFields(TestBarLink.class, "barId", TestBar.class, "bazId", TestBaz.class)
                    .withFieldNames("bazs", "bars")
            )
        );

        assertThat(manyToManyJSON(domain), is(DECLARED));
    }


    /// A null name leaves that end without a field.
    @Test
    void leavesAnEndWithoutAField()
    {
        final QLiveDomain domain = domain(
            b -> b.configureManyToMany(TEST_BAR_LINK.BAR_ID, TEST_BAR_LINK.BAZ_ID, "bazs", null)
        );

        assertThat(field(domain, "TestBar", "bazs"), is(notNullValue()));
        assertThat(type(domain, "TestBaz").getFieldDefinition("bars"), is((Object) null));
    }


    @Test
    void refusesADeclarationWithoutFields()
    {
        refuses(
            b -> b.configureManyToMany(TEST_BAR_LINK.BAR_ID, TEST_BAR_LINK.BAZ_ID, null, null),
            "needs a field on at least one of its ends"
        );
    }


    @Test
    void refusesLinkColumnsOfDifferentTables()
    {
        refuses(
            b -> b.configureManyToMany(TEST_BAR_LINK.BAR_ID, TEST_FOO.OWNER_ID, "a", "b"),
            "must belong to the same table"
        );
    }


    /// The working set names rows by their id, so that is what a link column has to point at.
    @Test
    void refusesALinkColumnNotReferencingAnId()
    {
        refuses(
            b -> b.configureManyToMany(TEST_FOO.OWNER_ID, TEST_FOO.TYPE, "a", "b"),
            "has to reference the id of TestFooType"
        );
    }


    @Test
    void refusesTheSameLinkColumnTwice()
    {
        refuses(
            b -> b.withManyToMany(
                new ManyToManyBuilder()
                    .withPojoFields(TestBarLink.class, "barId", TestBar.class, "barId", TestBar.class)
                    .withFieldNames("a", "b")
            ),
            "must differ"
        );
    }


    /// Both ends of a self-referential many-to-many are the same type, which can't have one field twice.
    @Test
    void refusesOneNameForBothEndsOfTheSameType()
    {
        refuses(
            b -> b.withManyToMany(
                new ManyToManyBuilder()
                    .withPojoFields(TestBarLink.class, "barId", TestBar.class, "bazId", TestBar.class)
                    .withFieldNames("related", "related")
            ),
            "need different names"
        );
    }


    // -----------------------------------------------------------------------------------------------------

    private static QLiveDomain domain(UnaryOperator<QLiveDomainBuilder> declaration)
    {
        return declaration.apply(
            QLiveDefaultDomain.newDomain(null, List.of()).objectTypes(Public.PUBLIC)
        ).build();
    }


    private static void refuses(UnaryOperator<QLiveDomainBuilder> declaration, String message)
    {
        final QLiveDomainBuilderException e = assertThrows(
            QLiveDomainBuilderException.class,
            () -> domain(declaration)
        );
        assertThat(e.getMessage(), containsString(message));
    }


    private static String manyToManyJSON(QLiveDomain domain)
    {
        return JSONUtil.DEFAULT_GENERATOR.forValue(domain.getMetaData().getManyToManyModels());
    }


    private static GraphQLObjectType type(QLiveDomain domain, String name)
    {
        return (GraphQLObjectType) domain.getGraphQLSchema().getType(name);
    }


    private static GraphQLFieldDefinition field(QLiveDomain domain, String type, String name)
    {
        return type(domain, type).getFieldDefinition(name);
    }
}
