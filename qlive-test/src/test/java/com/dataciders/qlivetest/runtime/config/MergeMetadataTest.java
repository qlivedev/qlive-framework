package com.dataciders.qlivetest.runtime.config;

import com.dataciders.qlive.runtime.meta.MergeMeta;
import com.dataciders.qlivetest.runtime.logic.QueryLogic;
import de.quinscape.domainql.DomainQL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

/// Runs the application's merge declarations against the application's own schema, built from the same
/// logic beans the application runs on. The domain builds without a database as long as no query executes,
/// so this needs neither a Spring context nor rows.
///
/// Two things it guards. The declarations have to hold: a type that lost its version column or a field that
/// got renamed makes the provider raise, and this is where that is noticed rather than at the next startup.
/// And which types take part is derived from the schema, so this is also the test that the version columns
/// are where the merge design says they are.
class MergeMetadataTest
{
    private static DomainQL domainQL;


    @BeforeAll
    static void buildDomain() throws IOException
    {
        // QueryLogic carries the type list that puts the hand-written Qux in the generated POJO's place, so
        // leaving it out would build a schema the application never runs. Nothing calls into it here, which
        // is why it can be handed a null service.
        domainQL = DomainQLConfiguration.newDomainQL(
            null,
            List.of(new QueryLogic(null)),
            List.of(new ExampleMetadataProvider(), DomainQLConfiguration.newMergeMetadata())
        );
    }


    /// Nothing declares this and nothing can. The version column is the declaration, so the list is a census
    /// of the schema rather than of anybody's configuration.
    @Test
    void derivesWhichTypesTakePart()
    {
        assertThat(MergeMeta.versionedTypes(domainQL), contains("Bar", "BarLink", "Baz", "Foo"));

        // left out on purpose, so that writing an unversioned type has a subject here
        assertThat(MergeMeta.isVersioned(domainQL, "Qux"), is(false));
        assertThat(MergeMeta.isVersioned(domainQL, "FooType"), is(false));
    }


    @Test
    void declaresWhatTheApplicationDecides()
    {
        assertThat(MergeMeta.resolvesConflicts(domainQL, "Bar"), is(true));
        assertThat(MergeMeta.resolvesConflicts(domainQL, "Baz"), is(true));
        assertThat(MergeMeta.ignoredFields(domainQL, "Foo"), contains("created"));
    }


    /// A versioned type that declares nothing still detects conflicts and still merges what does not
    /// overlap; what it does not do is hand a user two values to choose between.
    @Test
    void leavesTheRestAtWhatTheFrameworkDoesAnyway()
    {
        assertThat(MergeMeta.resolvesConflicts(domainQL, "Foo"), is(false));
        assertThat(MergeMeta.isAutoMerge(domainQL, "Foo"), is(true));
        assertThat(MergeMeta.ignoredFields(domainQL, "Bar"), is(List.of()));
    }


    /// bar_link is a link of the plain shape -- an id, a version and its two foreign keys -- so the client
    /// recognises it without help and nothing here declares it one.
    @Test
    void declaresNoLinkType()
    {
        assertThat(MergeMeta.isLinkType(domainQL, "BarLink"), is(false));
    }
}
