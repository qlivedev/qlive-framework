package com.dataciders.qlivetest.runtime;

import com.dataciders.qlive.model.merge.EntityChange;
import com.dataciders.qlive.model.merge.EntityDeletion;
import com.dataciders.qlive.model.merge.FieldChange;
import com.dataciders.qlive.model.merge.MergeConfig;
import com.dataciders.qlive.model.merge.MergeConflict;
import com.dataciders.qlive.model.merge.MergeConflictField;
import com.dataciders.qlive.model.merge.MergeResult;
import com.dataciders.qlive.model.merge.MergeStatus;
import com.dataciders.qlive.runtime.QLiveException;
import com.dataciders.qlive.runtime.merge.MergeService;
import com.dataciders.qlive.runtime.merge.VersionHolder;
import de.quinscape.domainql.generic.GenericScalar;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.dataciders.qlivetest.domain.Tables.APP_VERSION;
import static com.dataciders.qlivetest.domain.Tables.BAR;
import static com.dataciders.qlivetest.domain.Tables.BAR_LINK;
import static com.dataciders.qlivetest.domain.Tables.BAZ;
import static com.dataciders.qlivetest.domain.Tables.FOO;
import static com.dataciders.qlivetest.domain.Tables.QUX;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/// Runs merges against this application's own database, because an optimistic lock is a claim about what a
/// second writer sees and nothing short of a second write proves it.
///
/// Every row here is one the test made. The example data is what the other tests assert about, so nothing
/// below touches it, and what was made is removed again whether the test passed or not.
@SpringBootTest
class MergeServiceTest
{
    @Autowired
    private MergeService mergeService;

    @Autowired
    private DSLContext dslContext;

    @Autowired
    private VersionHolder versionHolder;

    /// Ids of the rows this test created, for the cleanup that has to run either way.
    private final List<String> bars = new ArrayList<>();

    private final List<String> barLinks = new ArrayList<>();

    private final List<String> quxs = new ArrayList<>();


    @AfterEach
    void removeWhatWasMade()
    {
        dslContext.deleteFrom(BAR_LINK).where(BAR_LINK.ID.in(barLinks)).execute();
        dslContext.deleteFrom(BAR).where(BAR.ID.in(bars)).execute();
        dslContext.deleteFrom(QUX).where(QUX.ID.in(quxs)).execute();
    }


    /// A new row and then a change to it, which is the whole ordinary path: the insert writes the version
    /// the row is now in, and the change is only accepted against that one.
    @Test
    void writesANewRowAndThenChangesIt()
    {
        final String id = newId(bars);

        assertThat(
            merge(
                newBar(id, "Merge #1", 1)
            ).getStatus(),
            is(MergeStatus.DONE)
        );

        final Record stored = bar(id);
        assertThat(stored.get(BAR.NAME), is("Merge #1"));
        assertThat(stored.get(BAR.NUM), is(1));
        assertThat(stored.get(BAR.VERSION), is(notNullValue()));

        assertThat(
            merge(
                change("Bar", id, stored.get(BAR.VERSION), field("name", "String", "Merge #1 renamed"))
            ).getStatus(),
            is(MergeStatus.DONE)
        );

        assertThat(bar(id).get(BAR.NAME), is("Merge #1 renamed"));

        // the version moved with the write, which is what makes the base it was written against spent
        assertThat(bar(id).get(BAR.VERSION), is(not(stored.get(BAR.VERSION))));
    }


    /// The lock itself, and what the mask narrows it to. The row moved since it was read, the statement
    /// matches nothing, and of the two fields this change wanted to write only the one the other write also
    /// touched is a decision.
    @Test
    void refusesAChangeAgainstAVersionThatMoved()
    {
        final String id = newId(bars);
        merge(newBar(id, "Merge #2", 2));

        final String base = bar(id).get(BAR.VERSION);

        // somebody else saves first and goes home
        merge(change("Bar", id, base, field("num", "Int", 22)));

        final String moved = bar(id).get(BAR.VERSION);

        final MergeResult result = merge(
            change("Bar", id, base, field("name", "String", "never written"), field("num", "Int", 99))
        );

        assertThat(result.getStatus(), is(MergeStatus.CONFLICT));
        assertThat(result.getConflicts(), hasSize(1));

        final MergeConflict conflict = result.getConflicts().get(0);
        assertThat(conflict.getType(), is("Bar"));
        assertThat(conflict.getId(), is(id));
        assertThat(conflict.isDeleted(), is(false));

        // the base a second attempt has to be made against
        assertThat(conflict.getVersion(), is(moved));

        // 'name' is nobody else's opinion, so there is nothing to decide about it
        assertThat(fieldNames(conflict), contains("num"));

        // and nothing was written, the merge being all or nothing however few fields clashed
        assertThat(bar(id).get(BAR.NAME), is("Merge #2"));
        assertThat(bar(id).get(BAR.NUM), is(22));
    }


    /// The case the whole mechanism exists for, and the common one. Somebody else changed a field this
    /// change never touched, so both edits belong in the row: the write is simply made again against the
    /// version they left behind, and nobody is asked anything.
    @Test
    void mergesOverAChangeThatTouchedOtherFields()
    {
        final String id = newId(bars);
        merge(newBar(id, "Merge #11", 11));

        final String base = bar(id).get(BAR.VERSION);

        // somebody else saves first and goes home
        merge(change("Bar", id, base, field("num", "Int", 111)));

        assertThat(
            merge(change("Bar", id, base, field("name", "String", "Merge #11 renamed"))).getStatus(),
            is(MergeStatus.DONE)
        );

        final Record stored = bar(id);
        assertThat(stored.get(BAR.NAME), is("Merge #11 renamed"));
        assertThat(stored.get(BAR.NUM), is(111));
    }


    /// A real conflict names the fields both writes touched. The ones only theirs touched come with it
    /// marked informational -- the merge takes those silently, and they are here so a form can show what
    /// moved under the user rather than only what clashed.
    @Test
    void attachesWhatMovedBesidesWhatClashed()
    {
        final String id = newId(bars);
        merge(newBar(id, "Merge #12", 12));

        final String base = bar(id).get(BAR.VERSION);
        merge(
            change(
                "Bar", id, base,
                field("name", "String", "saved by somebody else"),
                field("num", "Int", 122)
            )
        );

        final MergeConflict conflict = merge(
            resolving(),
            change("Bar", id, base, field("name", "String", "typed by me"))
        ).getConflicts().get(0);

        assertThat(fieldNames(conflict), contains("name", "num"));

        final MergeConflictField name = conflictField(conflict, "name");
        assertThat(name.isInformational(), is(false));
        assertThat(name.getMine().getValue(), is("typed by me"));
        assertThat(name.getStored().getValue(), is("saved by somebody else"));

        // nobody here has an opinion about num, so there is nothing to decide and no value of ours to carry
        final MergeConflictField num = conflictField(conflict, "num");
        assertThat(num.isInformational(), is(true));
        assertThat(num.getMine(), is(nullValue()));
        assertThat(num.getStored().getValue(), is(122));
    }


    /// What an expired base version costs. Without a record of the other write there is no telling which
    /// fields it touched, so every field this change touched is named -- the conservative answer, and the
    /// only one available.
    @Test
    void assumesEveryFieldWhereTheRecordIsGone()
    {
        final String id = newId(bars);
        merge(newBar(id, "Merge #13", 13));

        final String base = bar(id).get(BAR.VERSION);
        merge(change("Bar", id, base, field("num", "Int", 133)));

        // the record of that write outlives its usefulness and goes, from the table and from the memory in
        // front of it
        dslContext.deleteFrom(APP_VERSION).where(APP_VERSION.ENTITY_ID.eq(id)).execute();
        versionHolder.dropOlderThan(Timestamp.from(Instant.now().plusSeconds(1)));

        final MergeConflict conflict = merge(
            change("Bar", id, base, field("name", "String", "never written"))
        ).getConflicts().get(0);

        // 'name' would have merged silently a moment ago
        assertThat(fieldNames(conflict), contains("name"));
        assertThat(bar(id).get(BAR.NAME), is("Merge #13"));
    }


    /// Bar declares that it resolves conflicts in the view and the caller says it is such a view, so both
    /// values come back per field -- which is the whole of what a form needs in order to offer a choice.
    @Test
    void carriesBothValuesWhereTypeAndCallerBothAskedFor()
    {
        final String id = newId(bars);
        merge(newBar(id, "Merge #3", 3));

        final String base = bar(id).get(BAR.VERSION);
        merge(change("Bar", id, base, field("name", "String", "saved by somebody else")));

        final MergeResult result = merge(
            resolving(),
            change("Bar", id, base, field("name", "String", "typed by me"))
        );

        final MergeConflictField field = result.getConflicts().get(0).getFields().get(0);

        assertThat(field.getField(), is("name"));
        assertThat(field.getMine().getValue(), is("typed by me"));
        assertThat(field.getStored().getType(), is("String"));
        assertThat(field.getStored().getValue(), is("saved by somebody else"));
    }


    /// The same conflict to a caller that cannot put it in front of anybody: which fields clashed, and not
    /// a copy of the row it has no use for.
    @Test
    void namesTheFieldsAndNothingElseOtherwise()
    {
        final String id = newId(bars);
        merge(newBar(id, "Merge #4", 4));

        final String base = bar(id).get(BAR.VERSION);
        merge(change("Bar", id, base, field("name", "String", "saved by somebody else")));

        final MergeConflictField field = merge(
            change("Bar", id, base, field("name", "String", "typed by me"))
        ).getConflicts().get(0).getFields().get(0);

        assertThat(field.getField(), is("name"));
        assertThat(field.getMine(), is(nullValue()));
        assertThat(field.getStored(), is(nullValue()));
    }


    /// Foo declares 'created' ignored -- it is written once and no two users can hold different opinions
    /// about it -- so a conflict never names it, however the write went.
    @Test
    void neverNamesAnIgnoredField()
    {
        final Record foo = dslContext.selectFrom(FOO).limit(1).fetchOne();

        final MergeResult result = merge(
            change(
                "Foo",
                foo.get(FOO.ID),
                UUID.randomUUID().toString(),
                field("name", "String", "never written"),
                field("created", "Timestamp", Timestamp.valueOf("2020-01-01 00:00:00"))
            )
        );

        assertThat(result.getStatus(), is(MergeStatus.CONFLICT));
        assertThat(fieldNames(result.getConflicts().get(0)), contains("name"));

        assertThat(dslContext.selectFrom(FOO).where(FOO.ID.eq(foo.get(FOO.ID))).fetchOne().get(FOO.NAME),
            is(foo.get(FOO.NAME)));
    }


    /// All or nothing. One row of the working set is behind, so the other one is not written either -- the
    /// user gets everything back the way they typed it rather than half of it stored and half of it not.
    @Test
    void rollsBackEverythingWhenOneRowConflicts()
    {
        final String kept = newId(bars);
        final String behind = newId(bars);

        merge(newBar(kept, "Merge #5", 5), newBar(behind, "Merge #6", 6));

        final String staleBase = bar(behind).get(BAR.VERSION);
        merge(change("Bar", behind, staleBase, field("num", "Int", 66)));

        final MergeResult result = merge(
            change("Bar", kept, bar(kept).get(BAR.VERSION), field("num", "Int", 55)),
            change("Bar", behind, staleBase, field("num", "Int", 666))
        );

        assertThat(result.getStatus(), is(MergeStatus.CONFLICT));
        assertThat(result.getConflicts(), hasSize(1));
        assertThat(result.getConflicts().get(0).getId(), is(behind));

        // the row that was perfectly writable stayed as it was
        assertThat(bar(kept).get(BAR.NUM), is(5));
    }


    /// Qux carries no version column, which is the whole of how a type opts out. It is written the same way
    /// and last write wins, exactly as it did before there was a merge at all.
    @Test
    void writesAnUnversionedTypeWithoutALock()
    {
        final String id = newId(quxs);

        assertThat(
            merge(
                create("Qux", id, field("name", "String", "Merge Qux"), field("intValue", "Int", 7))
            ).getStatus(),
            is(MergeStatus.DONE)
        );

        assertThat(
            merge(
                change("Qux", id, null, field("intValue", "Int", 8))
            ).getStatus(),
            is(MergeStatus.DONE)
        );

        final Record stored = dslContext.selectFrom(QUX).where(QUX.ID.eq(id)).fetchOne();
        assertThat(stored.get(QUX.NAME), is("Merge Qux"));
        assertThat(stored.get(QUX.INT_VALUE), is(8));
    }


    /// The client generates the ids of new rows, so a change may name a row nothing has created yet. The
    /// order the changes arrive in is not that order and is not meant to be.
    @Test
    void insertsANewRowBeforeTheRowsPointingAtIt()
    {
        final String barId = newId(bars);
        final String linkId = newId(barLinks);
        final String bazId = dslContext.select(BAZ.ID).from(BAZ).limit(1).fetchOne(BAZ.ID);

        final MergeResult result = merge(
            create(
                "BarLink",
                linkId,
                field("barId", "String", barId),
                field("bazId", "String", bazId)
            ),
            newBar(barId, "Merge #7", 7)
        );

        assertThat(result.getStatus(), is(MergeStatus.DONE));
        assertThat(
            dslContext.selectFrom(BAR_LINK).where(BAR_LINK.ID.eq(linkId)).fetchOne().get(BAR_LINK.BAR_ID),
            is(barId)
        );
    }


    /// A deletion is the same lock, and a row that is not there any more is a conflict of its own kind:
    /// there is nothing to merge into and nothing to choose between.
    @Test
    void removesARowAndThenReportsItGone()
    {
        final String id = newId(bars);
        merge(newBar(id, "Merge #8", 8));

        final String version = bar(id).get(BAR.VERSION);

        assertThat(merge(List.of(), List.of(deletion("Bar", id, version))).getStatus(), is(MergeStatus.DONE));
        assertThat(bar(id), is(nullValue()));

        final MergeConflict conflict = merge(
            change("Bar", id, version, field("name", "String", "too late"))
        ).getConflicts().get(0);

        assertThat(conflict.isDeleted(), is(true));
        assertThat(conflict.getVersion(), is(nullValue()));
        assertThat(conflict.getFields(), is(empty()));
    }


    /// A row that moved since it was read may not be the row the user meant to delete, so the delete does
    /// not happen and the version standing now comes back with it.
    @Test
    void refusesADeletionOfARowThatMoved()
    {
        final String id = newId(bars);
        merge(newBar(id, "Merge #9", 9));

        final String base = bar(id).get(BAR.VERSION);
        merge(change("Bar", id, base, field("num", "Int", 99)));

        final MergeResult result = merge(List.of(), List.of(deletion("Bar", id, base)));

        assertThat(result.getStatus(), is(MergeStatus.CONFLICT));

        final MergeConflict conflict = result.getConflicts().get(0);
        assertThat(conflict.isDeleted(), is(false));
        assertThat(conflict.getVersion(), is(bar(id).get(BAR.VERSION)));
        assertThat(conflict.getFields(), is(empty()));

        assertThat(bar(id), is(notNullValue()));
    }


    /// A versioned row can only be written against the version it was read at, so a change without one is a
    /// query that forgot to select it -- which is worth hearing about here rather than as a lost update
    /// later.
    @Test
    void refusesAChangeWithNoBaseVersion()
    {
        final String id = newId(bars);
        merge(newBar(id, "Merge #10", 10));

        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> merge(change("Bar", id, null, field("num", "Int", 1)))
        );

        assertThat(e.getMessage(), containsString("version"));
    }


    /// The guard for the service that writes a table itself. bar is versioned, so writing it directly would
    /// leave its version saying the row is in a state it is not in.
    @Test
    void refusesToLetAVersionedTableBeWrittenDirectly()
    {
        assertThrows(QLiveException.class, () -> mergeService.ensureNotVersioned(BAR));

        // qux is not versioned and never took part, so nothing stands in the way of writing it
        mergeService.ensureNotVersioned(QUX);
    }


    // -----------------------------------------------------------------------------------------------------

    private MergeResult merge(EntityChange... changes)
    {
        return mergeService.merge(List.of(changes), List.of(), new MergeConfig());
    }


    private MergeResult merge(MergeConfig config, EntityChange... changes)
    {
        return mergeService.merge(List.of(changes), List.of(), config);
    }


    private MergeResult merge(List<EntityChange> changes, List<EntityDeletion> deletions)
    {
        return mergeService.merge(changes, deletions, new MergeConfig());
    }


    private static MergeConfig resolving()
    {
        final MergeConfig config = new MergeConfig();
        config.setResolveConflicts(true);

        return config;
    }


    /// A new bar with everything its not-null columns need.
    private static EntityChange newBar(String id, String name, int num)
    {
        return create(
            "Bar",
            id,
            field("name", "String", name),
            field("num", "Int", num),
            field("created", "Timestamp", Timestamp.valueOf("2026-09-10 12:00:00"))
        );
    }


    private static EntityChange create(String type, String id, FieldChange... fields)
    {
        final EntityChange change = change(type, id, null, fields);
        change.setNew(true);

        return change;
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


    private static EntityDeletion deletion(String type, String id, String version)
    {
        final EntityDeletion deletion = new EntityDeletion();
        deletion.setType(type);
        deletion.setId(id);
        deletion.setVersion(version);

        return deletion;
    }


    private static MergeConflictField conflictField(MergeConflict conflict, String name)
    {
        return conflict.getFields().stream()
            .filter(field -> field.getField().equals(name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No field '" + name + "' in " + conflict));
    }


    private static List<String> fieldNames(MergeConflict conflict)
    {
        return conflict.getFields().stream().map(MergeConflictField::getField).sorted().toList();
    }


    /// A fresh id, remembered so that the row it will name is removed again.
    private static String newId(List<String> cleanup)
    {
        final String id = UUID.randomUUID().toString();
        cleanup.add(id);

        return id;
    }


    private Record bar(String id)
    {
        return dslContext.selectFrom(BAR).where(BAR.ID.eq(id)).fetchOne();
    }
}
