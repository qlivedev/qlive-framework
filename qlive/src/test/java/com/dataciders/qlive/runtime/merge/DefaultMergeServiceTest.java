package com.dataciders.qlive.runtime.merge;

import com.dataciders.qlive.model.merge.EntityChange;
import com.dataciders.qlive.model.merge.EntityDeletion;
import com.dataciders.qlive.model.merge.FieldChange;
import com.dataciders.qlive.model.merge.MergeConfig;
import com.dataciders.qlive.runtime.QLiveException;
import com.dataciders.qlive.runtime.domain.TestDomainConfig;
import com.dataciders.qlive.runtime.domain.TestLogic;
import com.dataciders.qlive.testdomain.Tables;
import de.quinscape.domainql.DomainQL;
import de.quinscape.domainql.generic.GenericScalar;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// What the merge refuses before it writes anything, and why every one of these is worth a failure rather
/// than a best guess: each of them is a working set that cannot mean what it says, and the cheapest moment
/// to say so is before the first statement.
///
/// The service is built on a domain with no database behind it, which is all it takes -- everything below
/// is answered out of the schema, and none of it reaches a statement, a field layout or a version record.
class DefaultMergeServiceTest
{
    private static MergeService mergeService;


    @BeforeAll
    static void buildService()
    {
        final DomainQL domainQL = TestDomainConfig.domainQL(new TestLogic());

        mergeService = new DefaultMergeService(domainQL, null, null, null);
    }


    /// The merge writes the tables the domain exposes, under the names it exposes them under. A name that
    /// is none of those is not a type the application forgot to register, it is a typo or a stale client.
    @Test
    void refusesATypeTheDomainDoesNotExpose()
    {
        assertThat(
            refused(change("TestBar", "id-1", "v-1", field("name", "String", "x"))),
            containsString("TestBar")
        );
    }


    /// A change writes columns. A field name that matches none says nothing about what to write, and
    /// writing what is left over would silently store an incomplete row.
    @Test
    void refusesAFieldThatIsNoColumn()
    {
        assertThat(
            refused(change("TestFoo", "id-1", "v-1", field("nose", "String", "x"))),
            containsString("nose")
        );
    }


    /// The one new obligation the merge puts on a query: a row that is to be edited selects its version.
    /// Without one there is nothing to hold the write to, and letting it through would be the lost update
    /// this whole mechanism exists to prevent.
    @Test
    void refusesAChangeWithNoBaseVersion()
    {
        assertThat(
            refused(change("TestFoo", "id-1", null, field("name", "String", "x"))),
            containsString("version")
        );
    }


    /// The version is the merge's to write. A client that sets it is either replaying an old row or
    /// deciding for itself that it won the race.
    @Test
    void refusesToBeToldTheVersion()
    {
        assertThat(
            refused(change("TestFoo", "id-1", "v-1", field("version", "String", "v-2"))),
            containsString("version")
        );
    }


    /// A row does not change its identity, so an id among the fields either repeats the change's own id or
    /// contradicts it.
    @Test
    void refusesAnIdThatDisagreesWithTheChange()
    {
        assertThat(
            refused(change("TestFoo", "id-1", "v-1", field("id", "String", "id-2"))),
            containsString("identity")
        );
    }


    /// A deletion is held to the same version as a change, and for a stronger reason: a row that moved may
    /// not be the row the user meant to remove.
    @Test
    void refusesADeletionWithNoVersion()
    {
        final EntityDeletion deletion = new EntityDeletion();
        deletion.setType("TestFoo");
        deletion.setId("id-1");

        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> mergeService.merge(List.of(), List.of(deletion), new MergeConfig())
        );

        assertThat(e.getMessage(), containsString("version"));
    }


    /// The guard for the service with its own reason to write a table. A versioned row written past the
    /// merge keeps a version that no longer describes it, and the next merge overwrites that write while
    /// believing itself up to date.
    @Test
    void refusesToLetAVersionedTableBeWrittenDirectly()
    {
        assertThat(
            assertThrows(
                QLiveException.class,
                () -> mergeService.ensureNotVersioned(Tables.TEST_FOO)
            ).getMessage(),
            containsString("TestFoo")
        );

        // no version column, so it never took part and there is nothing to corrupt
        mergeService.ensureNotVersioned(Tables.TEST_USER);
    }


    // -----------------------------------------------------------------------------------------------------

    private static String refused(EntityChange change)
    {
        return assertThrows(
            QLiveException.class,
            () -> mergeService.merge(List.of(change), List.of(), new MergeConfig())
        ).getMessage();
    }


    private static EntityChange change(String type, String id, String version, FieldChange... fields)
    {
        final EntityChange change = new EntityChange();
        change.setType(type);
        change.setId(id);
        change.setVersion(version);
        change.setChanges(List.of(fields));

        return change;
    }


    private static FieldChange field(String name, String scalarType, Object value)
    {
        final FieldChange change = new FieldChange();
        change.setField(name);
        change.setValue(new GenericScalar(scalarType, value));

        return change;
    }
}
