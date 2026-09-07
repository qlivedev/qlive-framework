package com.dataciders.qlivetest.runtime.config;

import com.dataciders.qlivetest.runtime.logic.QueryLogic;
import de.quinscape.domainql.meta.DomainQLMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/// Runs the application's own metadata provider against the application's own schema, built from the same
/// logic beans the application runs on. The domain builds without a database as long as no query executes,
/// so this needs neither a Spring context nor rows.
///
/// What it guards is the agreement between the provider and frontend/src/qlive-meta.d.ts: the names written
/// here are the names TypeScript is told to expect, and nothing but a test on this side notices when the two
/// drift apart.
class ExampleMetadataProviderTest
{
    private static DomainQLMeta meta;


    @BeforeAll
    static void buildDomain() throws IOException
    {
        // QueryLogic carries the type list that puts the hand-written Qux in the generated POJO's place, so
        // leaving it out would build a schema the application never runs. Nothing calls into it here, which is
        // why it can be handed a null service.
        meta = GraphQLConfiguration.newDomainQL(
            null,
            List.of(new QueryLogic(null)),
            List.of(new ExampleMetadataProvider())
        ).getMetaData();
    }


    /// The addendum sits next to the builtin ones and lists the domain types with a name field. Asserted by
    /// the rule rather than by a census, so that adding a type to the example domain is not a test change.
    @Test
    void listsTheQuickSearchTypes()
    {
        assertThat(quickSearchTypes(), hasItems("Bar", "Baz", "Foo", "FooType"));

        // AppUser names its users "login"
        assertThat(quickSearchTypes(), not(hasItem("AppUser")));
    }


    /// Every listed type carries the field meta data marking what the search matches against, and only that
    /// field carries it.
    @Test
    void marksTheQuickSearchField()
    {
        for (String typeName : quickSearchTypes())
        {
            assertThat(
                typeName,
                meta.getTypeMeta(typeName).getFieldMeta("name", ExampleMetadataProvider.QUICK_SEARCH),
                is(true)
            );
        }

        assertThat(meta.getTypeMeta("Foo").getFieldMeta("id", ExampleMetadataProvider.QUICK_SEARCH), is(nullValue()));
    }


    /// A type taking no part in the quick search gets no field meta data at all, which is what lets the
    /// client-side type declare "fields" as optional.
    @Test
    void leavesTypesWithoutANameFieldAlone()
    {
        assertThat(meta.getTypeMeta("AppUser").getFields(), is(nullValue()));
    }


    @SuppressWarnings("unchecked")
    private static List<String> quickSearchTypes()
    {
        return (List<String>) meta.getData().get(ExampleMetadataProvider.QUICK_SEARCH_TYPES);
    }
}
