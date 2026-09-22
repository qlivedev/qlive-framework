package io.github.qlivedev.runtime.meta;

import io.github.qlivedev.runtime.QLiveException;
import io.github.qlivedev.runtime.domain.TestDomainConfig;
import io.github.qlivedev.runtime.domain.TestLogic;
import io.github.qlivedev.testdomain.tables.pojos.TestFoo;
import io.github.qlivedev.testdomain.tables.pojos.TestUser;
import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.graphql.meta.MetadataProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// What a type says about merging it, from the declaration an application writes to the meta data the
/// server and the client read it back out of. TestFoo is the test domain's versioned type, TestUser and
/// TestFooType are not.
class MergeMetadataProviderTest
{
    /// Taking part is a property of the schema, so nothing has to be declared and nothing can be forgotten.
    @Test
    void derivesWhichTypesTakePart()
    {
        final QLiveDomain domainQL = TestDomainConfig.domainQL(new TestLogic());

        assertThat(MergeMeta.isVersioned(domainQL, "TestFoo"), is(true));
        assertThat(MergeMeta.isVersioned(domainQL, "TestUser"), is(false));

        // by Java type, which is how a service writing rows of a POJO has the type in hand
        assertThat(MergeMeta.isVersioned(domainQL, TestFoo.class), is(true));
        assertThat(MergeMeta.isVersioned(domainQL, TestUser.class), is(false));

        // and a name that is no type of the domain, which is what a type name off the wire may be
        assertThat(MergeMeta.isVersioned(domainQL, "NoSuchType"), is(false));
        assertThat(MergeMeta.isVersioned(domainQL, Object.class), is(false));
    }


    @Test
    void listsTheVersionedTypes()
    {
        final QLiveDomain domainQL = TestDomainConfig.domainQL(new TestLogic());

        assertThat(MergeMeta.versionedTypes(domainQL), hasItem("TestFoo"));
        assertThat(MergeMeta.versionedTypes(domainQL), not(hasItem("TestUser")));

        // a query document is no entity, whatever its rows are
        assertThat(MergeMeta.versionedTypes(domainQL), not(hasItem("TestFooDocument")));
    }


    /// The four statements land in one map under one property, so that the type meta data namespace an
    /// application extends carries one merge entry rather than four ordinary words.
    @Test
    void writesTheDeclarationOntoTheType()
    {
        final QLiveDomain domainQL = domainWith(
            MergeMetadataProvider.newProvider()
                .resolveConflicts(TestFoo.class)
                .ignoreFields(TestFoo.class, "num", "created")
                .autoMerge(TestFoo.class, false)
        );

        assertThat(MergeMeta.resolvesConflicts(domainQL, "TestFoo"), is(true));
        assertThat(MergeMeta.isAutoMerge(domainQL, "TestFoo"), is(false));
        assertThat(MergeMeta.ignoredFields(domainQL, "TestFoo"), contains("created", "num"));
        assertThat(MergeMeta.isLinkType(domainQL, "TestFoo"), is(false));
    }


    /// What a type that declared nothing answers, which is what every type of an application without a
    /// provider answers.
    @Test
    void answersATypeThatDeclaredNothing()
    {
        final QLiveDomain domainQL = TestDomainConfig.domainQL(new TestLogic());

        assertThat(MergeMeta.resolvesConflicts(domainQL, "TestFoo"), is(false));
        assertThat(MergeMeta.ignoredFields(domainQL, "TestFoo"), is(List.of()));
        assertThat(MergeMeta.isLinkType(domainQL, "TestFoo"), is(false));

        // the one default that is not "off": a change that does not overlap ours is merged unless the type
        // asked to see it, because that case is what the mechanism is for
        assertThat(MergeMeta.isAutoMerge(domainQL, "TestFoo"), is(true));

        // and a name that is no type of the domain answers the same, rather than raising
        assertThat(MergeMeta.resolvesConflicts(domainQL, "NoSuchType"), is(false));
        assertThat(MergeMeta.isAutoMerge(domainQL, "NoSuchType"), is(true));
    }


    /// A type may be named by its GraphQL name where the application has no class for it, the way the query
    /// config meta data may.
    @Test
    void declaresByTypeName()
    {
        final QLiveDomain domainQL = domainWith(
            MergeMetadataProvider.newProvider().resolveConflicts("TestFoo")
        );

        assertThat(MergeMeta.resolvesConflicts(domainQL, "TestFoo"), is(true));
    }


    /// The one statement about a type that says nothing about versioning: a link row has no fields of its
    /// own to clash over, and a link table that carries some is the case auto-detection cannot see.
    @Test
    void declaresALinkTypeWithoutVersioning()
    {
        final QLiveDomain domainQL = domainWith(
            MergeMetadataProvider.newProvider().linkType(TestUser.class)
        );

        assertThat(MergeMeta.isLinkType(domainQL, "TestUser"), is(true));
    }


    /// Reported rather than written: a type without a version column takes no part, so everything but the
    /// link declaration would be meta data describing behavior the type can never reach -- which looks
    /// exactly like a column somebody forgot to add.
    @Test
    void reportsMergeBehaviourDeclaredForAnUnversionedType()
    {
        for (MergeMetadataProvider provider : List.of(
            MergeMetadataProvider.newProvider().resolveConflicts(TestUser.class),
            MergeMetadataProvider.newProvider().autoMerge(TestUser.class, false),
            MergeMetadataProvider.newProvider().ignoreFields(TestUser.class, "login")
        ))
        {
            final QLiveException e = assertThrows(QLiveException.class, () -> domainWith(provider));

            assertThat(e.getMessage(), containsString("TestUser"));
            assertThat(e.getMessage(), containsString("version"));
        }
    }


    @Test
    void reportsAnIgnoredFieldTheTypeDoesNotHave()
    {
        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> domainWith(
                MergeMetadataProvider.newProvider().ignoreFields(TestFoo.class, "noSuchField")
            )
        );

        assertThat(e.getMessage(), containsString("noSuchField"));
    }


    @Test
    void reportsADeclarationForSomethingThatIsNoType()
    {
        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> domainWith(MergeMetadataProvider.newProvider().resolveConflicts("NoSuchType"))
        );

        assertThat(e.getMessage(), containsString("NoSuchType"));
    }


    /// Several statements about one type accumulate; the same statement twice does not. It either repeats
    /// the first or contradicts it, and only this side can tell which was meant.
    @Test
    void reportsAStatementMadeTwice()
    {
        final MergeMetadataProvider provider = MergeMetadataProvider.newProvider()
            .resolveConflicts(TestFoo.class)
            .ignoreFields(TestFoo.class, "num");

        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> provider.resolveConflicts(TestFoo.class)
        );

        assertThat(e.getMessage(), containsString("TestFoo"));
    }


    /// The same type reached both ways is the same mistake, and only the built domain can see it: which
    /// GraphQL name a class ends up under is QLiveDomain's to decide.
    @Test
    void reportsATypeDeclaredByBothClassAndName()
    {
        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> domainWith(
                MergeMetadataProvider.newProvider()
                    .resolveConflicts(TestFoo.class)
                    .autoMerge("TestFoo", false)
            )
        );

        assertThat(e.getMessage(), containsString("TestFoo"));
    }


    /// Ignoring no fields is what a type that says nothing already does, so the empty call is a mistake
    /// rather than a way to say it.
    @Test
    void reportsIgnoredFieldsThatNameNoField()
    {
        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> MergeMetadataProvider.newProvider().ignoreFields(TestFoo.class)
        );

        assertThat(e.getMessage(), containsString("ignore"));
    }


    private static QLiveDomain domainWith(MetadataProvider provider)
    {
        return TestDomainConfig.domainQL(List.of(provider), new TestLogic());
    }
}
