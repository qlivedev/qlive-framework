package io.github.qlivedev.qlivetest.runtime.config;

import io.github.qlivedev.qlivetest.runtime.logic.QueryLogic;
import io.github.qlivedev.graphql.meta.DomainMeta;
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
    private static DomainMeta meta;


    @BeforeAll
    static void buildDomain() throws IOException
    {
        // QueryLogic carries the type list that puts the handwritten Qux in the generated POJO's place, so
        // leaving it out would build a schema the application never runs. Nothing calls into it here, which is
        // why it can be handed a null service.
        meta = QLiveDomainConfiguration.newDomain(
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


    /// A type taking no part in the quick search gets no quick search field meta data, which is what lets the
    /// client-side type declare "quickSearch" as optional. Its fields carry the builtin field meta data all
    /// the same.
    @Test
    void leavesTypesWithoutANameFieldAlone()
    {
        meta.getTypeMeta("AppUser").getFields().forEach(
            (fieldName, fieldMeta) -> assertThat(
                fieldName,
                fieldMeta.get(ExampleMetadataProvider.QUICK_SEARCH),
                is(nullValue())
            )
        );
    }


    @SuppressWarnings("unchecked")
    private static List<String> quickSearchTypes()
    {
        return (List<String>) meta.getData().get(ExampleMetadataProvider.QUICK_SEARCH_TYPES);
    }
}
