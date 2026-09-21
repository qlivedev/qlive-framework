package io.github.qlivedev.qlivetest.runtime;

import io.github.qlivedev.model.merge.EntityChange;
import io.github.qlivedev.model.merge.FieldChange;
import io.github.qlivedev.model.merge.MergeConfig;
import io.github.qlivedev.runtime.auth.AppAuthentication;
import io.github.qlivedev.runtime.merge.EntityVersionsEvent;
import io.github.qlivedev.runtime.merge.FieldLayout;
import io.github.qlivedev.runtime.merge.FieldLayoutService;
import io.github.qlivedev.runtime.merge.MergeService;
import io.github.qlivedev.runtime.merge.VersionCleanup;
import io.github.qlivedev.runtime.merge.VersionHolder;
import io.github.qlivedev.runtime.merge.VersionService;
import de.quinscape.domainql.generic.GenericScalar;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.github.qlivedev.qlivetest.domain.Tables.APP_FIELD_LAYOUT;
import static io.github.qlivedev.qlivetest.domain.Tables.APP_VERSION;
import static io.github.qlivedev.qlivetest.domain.Tables.BAR;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/// What a merge leaves behind besides the rows: one record per written row, saying which fields moved.
///
/// The record is what the next conflict reads to find out whether it is a conflict at all, and it is also
/// exactly what a push subscriber would need -- the type, the id, the new version and the mask. It is
/// published rather than handed over for that reason.
@SpringBootTest
@RecordApplicationEvents
class MergeVersionTest
{
    @Autowired
    private MergeService mergeService;

    @Autowired
    private VersionService versionService;

    @Autowired
    private VersionHolder versionHolder;

    @Autowired
    private FieldLayoutService fieldLayoutService;

    @Autowired
    private DSLContext dslContext;

    @Autowired
    private ApplicationEvents events;

    private final List<String> bars = new ArrayList<>();


    @AfterEach
    void removeWhatWasMade()
    {
        dslContext.deleteFrom(BAR).where(BAR.ID.in(bars)).execute();
        dslContext.deleteFrom(APP_VERSION).where(APP_VERSION.ENTITY_ID.in(bars)).execute();
    }


    /// The record of one change: what the row's version column now names, which fields the change touched,
    /// and the version it was made against.
    @Test
    void recordsWhichFieldsTheChangeTouched()
    {
        final String id = newId();
        merge(newBar(id, "Version #1", 1));

        final String base = bar(id).get(BAR.VERSION);

        merge(change(id, base, field("name", "String", "Version #1 renamed")));

        final String now = bar(id).get(BAR.VERSION);
        final Record record = version(now);

        assertThat(record.get(APP_VERSION.ENTITY_TYPE), is("Bar"));
        assertThat(record.get(APP_VERSION.ENTITY_ID), is(id));
        assertThat(record.get(APP_VERSION.PREV), is(base));
        assertThat(record.get(APP_VERSION.OWNER_ID), is(AppAuthentication.ANONYMOUS_ID));
        assertThat(record.get(APP_VERSION.CREATED), is(notNullValue()));

        final FieldLayout layout = fieldLayoutService.current("Bar");
        assertThat(record.get(APP_VERSION.FIELD_LAYOUT), is(layout.getId()));
        assertThat(layout.fields(record.get(APP_VERSION.FIELD_MASK)), contains("name"));
    }


    /// The insert of a new row is a recorded change like any other, and the one with nothing before it.
    @Test
    void recordsANewRowWithNothingBeforeIt()
    {
        final String id = newId();
        merge(newBar(id, "Version #2", 2));

        final Record record = version(bar(id).get(BAR.VERSION));

        assertThat(record.get(APP_VERSION.PREV), is(nullValue()));

        // 'created' is declared ignored by no type here, so all three fields the insert wrote are in
        assertThat(
            fieldLayoutService.current("Bar").fields(record.get(APP_VERSION.FIELD_MASK)),
            containsInAnyOrder("created", "name", "num")
        );
    }


    /// Published, not handed over. The in-memory holder is one listener today and a push module would be
    /// the second, reading the same records off the same event.
    @Test
    void publishesTheRecordsItWrote()
    {
        final String id = newId();
        merge(newBar(id, "Version #3", 3));

        final List<EntityVersionsEvent> published = events.stream(EntityVersionsEvent.class).toList();

        assertThat(published.size(), is(1));
        assertThat(published.get(0).getVersions().size(), is(1));
        assertThat(published.get(0).getVersions().get(0).getEntityId(), is(id));

        // and the holder heard it, which is what saves the chain walk a read
        assertThat(versionHolder.get(bar(id).get(BAR.VERSION)), is(notNullValue()));
    }


    /// A merge that conflicted wrote nothing, so there is no state anybody has to hear about.
    @Test
    void publishesNothingForAMergeThatRolledBack()
    {
        final String id = newId();
        merge(newBar(id, "Version #4", 4));

        final String base = bar(id).get(BAR.VERSION);
        merge(change(id, base, field("name", "String", "saved by somebody else")));

        final int before = (int) events.stream(EntityVersionsEvent.class).count();

        merge(change(id, base, field("name", "String", "typed by me")));

        assertThat((int) events.stream(EntityVersionsEvent.class).count(), is(before));
    }


    /// The cleanup, run with nothing granted a lifetime at all. Every record goes, from the table and from
    /// the holder, which is the state a base version older than the lifetime finds.
    ///
    /// It sweeps the whole table rather than this test's rows, which is what the scheduled one does too --
    /// version records are history and no other test asserts about them.
    @Test
    void expiresWhatOutlivedItsLifetime()
    {
        final String id = newId();
        merge(newBar(id, "Version #5", 5));

        final String version = bar(id).get(BAR.VERSION);
        assertThat(versionService.get(version), is(notNullValue()));

        new VersionCleanup(versionService, versionHolder, fieldLayoutService, Duration.ZERO).expire();

        assertThat(versionHolder.size(), is(0));
        assertThat(versionService.get(version), is(nullValue()));

        // the row and its version column stay, which is why nothing has a foreign key onto app_version
        assertThat(bar(id).get(BAR.VERSION), is(version));

        // and the layouts this deployment writes stay, being about to be named by the next merge
        assertThat(fieldLayoutService.current("Bar"), is(notNullValue()));
    }


    /// The sweep leaves alone what a node of a rolling deployment has stored but not yet written a record
    /// against.
    ///
    /// A node coming up stores its layouts at startup, and until its first merge nothing references them.
    /// To a node still on the old code such a layout is neither current nor referenced, so without the
    /// cutoff the old node would take it away between the new one storing it and using it -- and the
    /// records that follow would name a layout that is gone, permanently.
    @Test
    void leavesALayoutNothingHasUsedYet()
    {
        final String id = "0000000000000000000000000000000000000000000000000000000000000000";

        // what the other node's startup writes: a layout of a type this one does not have and no record
        // names, stored a moment ago
        dslContext.insertInto(APP_FIELD_LAYOUT)
            .columns(
                APP_FIELD_LAYOUT.ID,
                APP_FIELD_LAYOUT.ENTITY_TYPE,
                APP_FIELD_LAYOUT.FIELDS,
                APP_FIELD_LAYOUT.CREATED
            )
            .values(id, "Bar", "bazLinks,created,description,flag,id,name,num,version", now())
            .execute();

        try
        {
            new VersionCleanup(
                versionService, versionHolder, fieldLayoutService, Duration.ofDays(7)
            ).expire();

            assertThat(layout(id), is(notNullValue()));

            // once it is older than any surviving record could be, nothing can still be about to name it
            new VersionCleanup(versionService, versionHolder, fieldLayoutService, Duration.ZERO).expire();

            assertThat(layout(id), is(nullValue()));
        }
        finally
        {
            dslContext.deleteFrom(APP_FIELD_LAYOUT).where(APP_FIELD_LAYOUT.ID.eq(id)).execute();
        }
    }


    // -----------------------------------------------------------------------------------------------------

    private Record layout(String id)
    {
        return dslContext.selectFrom(APP_FIELD_LAYOUT).where(APP_FIELD_LAYOUT.ID.eq(id)).fetchOne();
    }


    private static Timestamp now()
    {
        return new Timestamp(System.currentTimeMillis());
    }


    private void merge(EntityChange... changes)
    {
        mergeService.merge(List.of(changes), List.of(), new MergeConfig());
    }


    private Record version(String versionId)
    {
        return dslContext.selectFrom(APP_VERSION).where(APP_VERSION.ID.eq(versionId)).fetchOne();
    }


    private Record bar(String id)
    {
        return dslContext.selectFrom(BAR).where(BAR.ID.eq(id)).fetchOne();
    }


    private String newId()
    {
        final String id = UUID.randomUUID().toString();
        bars.add(id);

        return id;
    }


    private static EntityChange newBar(String id, String name, int num)
    {
        final EntityChange change = change(
            id,
            null,
            field("name", "String", name),
            field("num", "Int", num),
            field("created", "Timestamp", Timestamp.valueOf("2026-09-10 12:00:00"))
        );
        change.setNew(true);

        return change;
    }


    private static EntityChange change(String id, String version, FieldChange... fields)
    {
        final EntityChange change = new EntityChange();
        change.setType("Bar");
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
