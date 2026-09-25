package io.github.qlivedev.runtime.meta;

import io.github.qlivedev.model.bootstrap.QLiveConfig;
import io.github.qlivedev.model.condition.Condition;
import io.github.qlivedev.model.condition.Field;
import io.github.qlivedev.model.condition.Value;
import io.github.qlivedev.runtime.QLiveException;
import io.github.qlivedev.runtime.domain.TestDomainConfig;
import io.github.qlivedev.runtime.domain.TestLogic;
import io.github.qlivedev.testdomain.tables.pojos.TestFoo;
import io.github.qlivedev.testdomain.tables.pojos.TestUser;
import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.graphql.meta.MetadataProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// What a type says about querying it, from the declaration an application writes to the meta data the
/// injections and the client read it back out of.
class QueryConfigMetadataProviderTest
{
    @Test
    void writesTheDeclaredDeltaOntoTheType()
    {
        final QLiveDomain domainQL = domainWith(
            QueryConfigMetadataProvider.newProvider()
                .forType(
                    TestFoo.class
                )
                .pageSize(20)
                .sortFields("name", "!created")
                .build()

        );

        final Map<String, Object> delta = QueryConfigMeta.deltaForType(domainQL, TestFoo.class);

        // only what the delta named, so everything else stays at what a query config says anyway
        assertThat(delta.keySet(), contains("pageSize", "sortFields"));
        assertThat(delta.get("pageSize"), is(20));
        assertThat((List<String>) delta.get("sortFields"), contains("name", "!created"));
    }


    @Test
    void isReachedThroughTheDocumentTypeOfTheRows()
    {
        // What the injections have in hand is the type a query returns, and what the application declared on
        // is the type of its rows.
        final QLiveDomain domainQL = domainWith(
            QueryConfigMetadataProvider.newProvider()
                .forType(TestFoo.class)
                    .pageSize(20)
                .build()
        );

        assertThat(
            QueryConfigMeta.deltaForDocumentType(domainQL, "TestFooDocument").get("pageSize"),
            is(20)
        );

        // a document type over rows that declare nothing, and something that is no document type at all
        assertThat(QueryConfigMeta.deltaForDocumentType(domainQL, "TestUserDocument"), is(nullValue()));
        assertThat(QueryConfigMeta.deltaForDocumentType(domainQL, "TestFoo"), is(nullValue()));
    }


    @Test
    void writesAConditionAsTheFilterDSLJSONItIs()
    {
        final Condition condition = new Condition();
        condition.setName("eq");
        condition.setOperands(
            List.of(
                new Field("name"),
                new Value("String", "foo")
            )
        );

        final QLiveDomain domainQL = domainWith(
            QueryConfigMetadataProvider.newProvider()
                .forType(TestFoo.class)
                .condition(condition)
                .build()
        );

        final Map<String, Object> written =
            (Map<String, Object>) QueryConfigMeta.deltaForType(domainQL, TestFoo.class).get("condition");

        // the shape the query config scalar reads back, i.e. the one the client would have sent
        assertThat(written.get("type"), is("Condition"));
        assertThat(written.get("name"), is("eq"));
        assertThat(
            ((List<Map<String, Object>>) written.get("operands")).getFirst().get("name"),
            is("name")
        );
    }


    @Test
    void writesTheDeclaredMaximumPageSizeOntoTheType()
    {
        final QLiveDomain domainQL = domainWith(
            QueryConfigMetadataProvider.newProvider()
                .forType(TestFoo.class)
                .pageSize(20)
                .maxPageSize(100)
                .build()
        );

        // beside the delta, not inside it: the delta is where a query starts, the maximum is how far it goes
        assertThat(QueryConfigMeta.deltaForType(domainQL, TestFoo.class).keySet(), contains("pageSize"));
        assertThat(QueryConfigMeta.maxPageSizeForType(domainQL, TestFoo.class), is(100));
    }


    @Test
    void readsTheMaximumPageSizeByJavaType()
    {
        // which is how a query has the type in hand: it returns rows of a POJO and never learns their
        // GraphQL name
        final QLiveDomain domainQL = domainWith(
            QueryConfigMetadataProvider.newProvider().forType(TestFoo.class).maxPageSize(100).build()
        );

        assertThat(QueryConfigMeta.maxPageSizeForType(domainQL, TestFoo.class), is(100));
        assertThat(QueryConfigMeta.maxPageSizeForType(domainQL, Object.class), is(0));
    }


    @Test
    void answersATypeThatDeclaredNoMaximumPageSize()
    {
        final QLiveDomain domainQL = domainWith(
            QueryConfigMetadataProvider.newProvider()
                .forType(TestFoo.class).pageSize(20).build()
        );

        // 0 is what a query config says when it wants every row, so a type that limits nothing and a query
        // that limits nothing are the same number
        assertThat(QueryConfigMeta.maxPageSizeForType(domainQL, TestFoo.class), is(0));
        assertThat(QueryConfigMeta.maxPageSizeForType(domainQL, QLiveConfig.class), is(0));
    }


    @Test
    void allowsDefiningAConfigForAllTypes()
    {
        final QLiveDomain domainQL = domainWith(
            QueryConfigMetadataProvider.newProvider()
                .forAllTypes().pageSize(20).build()
        );

        // 0 is what a query config says when it wants every row, so a type that limits nothing and a query
        // that limits nothing are the same number
        assertThat(QueryConfigMeta.deltaForType(domainQL, TestFoo.class).get("pageSize"), is(20));
        assertThat(QueryConfigMeta.deltaForType(domainQL, TestUser.class).get("pageSize"), is(20));
        assertThat(QueryConfigMeta.deltaForType(domainQL, QLiveConfig.class), is(nullValue()));
    }


    @Test
    void allTypesLosesToExplicit()
    {
        final QLiveDomain domainQL = domainWith(
            QueryConfigMetadataProvider.newProvider()
                .forType(TestFoo.class).pageSize(30)
                .andForAllTypes().pageSize(20)
                .build()
        );

        // 0 is what a query config says when it wants every row, so a type that limits nothing and a query
        // that limits nothing are the same number
        assertThat(QueryConfigMeta.deltaForType(domainQL, TestFoo.class).get("pageSize"), is(30));
        assertThat(QueryConfigMeta.deltaForType(domainQL, TestUser.class).get("pageSize"), is(20));
        assertThat(QueryConfigMeta.deltaForType(domainQL, QLiveConfig.class), is(nullValue()));
    }


    @Test
    void reportsAMaximumPageSizeThatLimitsNothing()
    {
        // 0 is how a config asks for every row, so a maximum of 0 would read as "at most all of them"
        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> QueryConfigMetadataProvider.newProvider().forType(TestFoo.class).maxPageSize(0).build()
        );

        assertThat(e.getMessage(), containsString("greater than 0"));
    }


    @Test
    void chainsStatementsAcrossTypes()
    {
        final QLiveDomain domainQL = domainWith(
            QueryConfigMetadataProvider.newProvider()
                .forAllTypes()
                    .pageSize(20)
                    .maxPageSize(500)
                .andForType(TestFoo.class)
                    .pageSize(30)
                    .maxPageSize(100)
                .andForTypes(TestUser.class)
                    .sortFields("login")
                    .build()
        );

        assertThat(QueryConfigMeta.deltaForType(domainQL, TestFoo.class).get("pageSize"), is(30));
        assertThat(QueryConfigMeta.maxPageSizeForType(domainQL, TestFoo.class), is(100));

        // named by a statement of its own, so its delta is that statement whole -- and the all-types page
        // size is not merged into it
        assertThat(QueryConfigMeta.deltaForType(domainQL, TestUser.class).get("sortFields"), is(List.of("login")));
        assertThat(QueryConfigMeta.deltaForType(domainQL, TestUser.class).get("pageSize"), is(nullValue()));

        // the maximum is a ceiling rather than a fallback, so a type that named none is held to the
        // all-types one even where it declared a delta of its own
        assertThat(QueryConfigMeta.maxPageSizeForType(domainQL, TestUser.class), is(500));
    }


    @Test
    void reportsATypeDeclaredTwice()
    {
        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> QueryConfigMetadataProvider.newProvider()
                .forType(TestFoo.class).pageSize(10)
                .andForType(TestFoo.class).pageSize(20)
                .build()
        );

        assertThat(e.getMessage(), containsString("TestFoo"));
        assertThat(e.getMessage(), containsString("twice"));
    }


    @Test
    void reportsAMaximumPageSizeForAllTypesThatLimitsNothing()
    {
        // the same rejection the per-type maximum gets -- an all-types statement is not a way around it
        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> QueryConfigMetadataProvider.newProvider().forAllTypes().maxPageSize(0).build()
        );

        assertThat(e.getMessage(), containsString("greater than 0"));
    }


    @Test
    void answersATypeThatDeclaredNothing()
    {
        final QLiveDomain domainQL = TestDomainConfig.domainNoMeta(new TestLogic());

        assertThat(QueryConfigMeta.deltaForType(domainQL, TestFoo.class), is(nullValue()));

        // and a name that is no type of the domain, which is what an injection asks about all the time
        assertThat(QueryConfigMeta.deltaForType(domainQL, QLiveConfig.class), is(nullValue()));
    }


    @Test
    void reportsADeclarationForSomethingThatIsNoType()
    {
        // Reported rather than written nowhere: a delta nothing ever reads looks exactly like one that does
        // not work, and only this side can tell the two apart.
        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> domainWith(
                QueryConfigMetadataProvider.newProvider()
                    .forType(QLiveConfig.class).pageSize(20).build()
            )
        );

        assertThat(e.getMessage(), containsString("Cannot configure: No query document type was declared for QLiveConfig"));

    }

    private static QLiveDomain domainWith(MetadataProvider provider)
    {
        return TestDomainConfig.domainQL(List.of(provider), new TestLogic());
    }
}
