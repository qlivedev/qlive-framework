package com.dataciders.qlive.runtime.meta;

import com.dataciders.qlive.model.condition.Condition;
import com.dataciders.qlive.model.condition.Field;
import com.dataciders.qlive.model.condition.Value;
import com.dataciders.qlive.runtime.QLiveException;
import com.dataciders.qlive.runtime.domain.TestDomainConfig;
import com.dataciders.qlive.runtime.domain.TestLogic;
import com.dataciders.qlive.testdomain.tables.pojos.TestFoo;
import de.quinscape.domainql.DomainQL;
import de.quinscape.domainql.meta.MetadataProvider;
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
        final DomainQL domainQL = domainWith(
            QueryConfigMetadataProvider.newProvider()
                .forType(
                    TestFoo.class,
                    QueryConfigDelta.newDelta()
                        .pageSize(20)
                        .sortFields("name", "!created")
                )
        );

        final Map<String, Object> delta = QueryConfigMeta.deltaForType(domainQL, "TestFoo");

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
        final DomainQL domainQL = domainWith(
            QueryConfigMetadataProvider.newProvider()
                .forType(TestFoo.class, QueryConfigDelta.newDelta().pageSize(20))
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

        final DomainQL domainQL = domainWith(
            QueryConfigMetadataProvider.newProvider()
                .forType(TestFoo.class, QueryConfigDelta.newDelta().condition(condition))
        );

        final Map<String, Object> written =
            (Map<String, Object>) QueryConfigMeta.deltaForType(domainQL, "TestFoo").get("condition");

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
        final DomainQL domainQL = domainWith(
            QueryConfigMetadataProvider.newProvider()
                .forType(TestFoo.class, QueryConfigDelta.newDelta().pageSize(20))
                .maxPageSize(TestFoo.class, 100)
        );

        // beside the delta, not inside it: the delta is where a query starts, the maximum is how far it goes
        assertThat(QueryConfigMeta.deltaForType(domainQL, "TestFoo").keySet(), contains("pageSize"));
        assertThat(QueryConfigMeta.maxPageSizeForType(domainQL, "TestFoo"), is(100));
    }


    @Test
    void readsTheMaximumPageSizeByJavaType()
    {
        // which is how a query has the type in hand: it returns rows of a POJO and never learns their
        // GraphQL name
        final DomainQL domainQL = domainWith(
            QueryConfigMetadataProvider.newProvider().maxPageSize("TestFoo", 100)
        );

        assertThat(QueryConfigMeta.maxPageSizeForType(domainQL, TestFoo.class), is(100));
        assertThat(QueryConfigMeta.maxPageSizeForType(domainQL, Object.class), is(0));
    }


    @Test
    void answersATypeThatDeclaredNoMaximumPageSize()
    {
        final DomainQL domainQL = domainWith(
            QueryConfigMetadataProvider.newProvider()
                .forType(TestFoo.class, QueryConfigDelta.newDelta().pageSize(20))
        );

        // 0 is what a query config says when it wants every row, so a type that limits nothing and a query
        // that limits nothing are the same number
        assertThat(QueryConfigMeta.maxPageSizeForType(domainQL, "TestFoo"), is(0));
        assertThat(QueryConfigMeta.maxPageSizeForType(domainQL, "NoSuchType"), is(0));
    }


    @Test
    void reportsAMaximumPageSizeThatLimitsNothing()
    {
        // 0 is how a config asks for every row, so a maximum of 0 would read as "at most all of them"
        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> QueryConfigMetadataProvider.newProvider().maxPageSize(TestFoo.class, 0)
        );

        assertThat(e.getMessage(), containsString("greater than 0"));
    }


    @Test
    void answersATypeThatDeclaredNothing()
    {
        final DomainQL domainQL = TestDomainConfig.domainQL(new TestLogic());

        assertThat(QueryConfigMeta.deltaForType(domainQL, "TestFoo"), is(nullValue()));

        // and a name that is no type of the domain, which is what an injection asks about all the time
        assertThat(QueryConfigMeta.deltaForType(domainQL, "NoSuchType"), is(nullValue()));
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
                    .forType("NoSuchType", QueryConfigDelta.newDelta().pageSize(20))
            )
        );

        assertThat(e.getMessage(), containsString("NoSuchType"));
    }


    @Test
    void reportsATypeDeclaredTwice()
    {
        final QueryConfigMetadataProvider provider = QueryConfigMetadataProvider.newProvider()
            .forType(TestFoo.class, QueryConfigDelta.newDelta().pageSize(20));

        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> provider.forType(TestFoo.class, QueryConfigDelta.newDelta().pageSize(50))
        );

        assertThat(e.getMessage(), containsString("TestFoo"));
    }


    private static DomainQL domainWith(MetadataProvider provider)
    {
        return TestDomainConfig.domainQL(List.of(provider), new TestLogic());
    }
}
