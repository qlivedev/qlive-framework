package io.github.qlivedev.qlivetest.runtime;

import io.github.qlivedev.qlive.model.merge.MergeConflict;
import io.github.qlivedev.qlive.runtime.auth.AppAuthentication;
import io.github.qlivedev.qlive.runtime.merge.EntityWriter;
import io.github.qlivedev.qlive.runtime.merge.MergeConflictException;
import io.github.qlivedev.qlivetest.domain.tables.pojos.Bar;
import io.github.qlivedev.qlivetest.domain.tables.pojos.Baz;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static io.github.qlivedev.qlivetest.domain.Tables.APP_VERSION;
import static io.github.qlivedev.qlivetest.domain.Tables.BAR;
import static io.github.qlivedev.qlivetest.domain.Tables.BAR_LINK;
import static io.github.qlivedev.qlivetest.domain.Tables.BAZ;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Writes rows from Java through the {@link EntityWriter}: in the caller's transaction, under the same versions the
/// client's merge holds to, recorded for whoever the security context holds.
@SpringBootTest
class EntityWriterTest
{
    @Autowired
    private EntityWriter entityWriter;

    @Autowired
    private DSLContext dslContext;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /// Ids of the rows this test made, for the cleanup that has to run either way.
    private final List<String> bars = new ArrayList<>();

    private final List<String> bazs = new ArrayList<>();


    @AfterEach
    void removeWhatWasMade()
    {
        final List<String> barLinks =
            dslContext.select(BAR_LINK.ID).from(BAR_LINK).where(BAR_LINK.BAR_ID.in(bars)).fetch(BAR_LINK.ID);

        dslContext.deleteFrom(BAR_LINK).where(BAR_LINK.ID.in(barLinks)).execute();
        dslContext.deleteFrom(BAR).where(BAR.ID.in(bars)).execute();
        dslContext.deleteFrom(BAZ).where(BAZ.ID.in(bazs)).execute();
        dslContext.deleteFrom(APP_VERSION)
            .where(APP_VERSION.ENTITY_ID.in(bars))
            .or(APP_VERSION.ENTITY_ID.in(bazs))
            .or(APP_VERSION.ENTITY_ID.in(barLinks))
            .execute();
    }


    /// An insert generates the id of a POJO without one, and leaves the POJO at the version it was written at,
    /// which a following update holds to.
    @Test
    void insertsAndUpdatesARow()
    {
        final Bar bar = newBar("Writer #1");

        inTransaction(writer -> writer.insert(bar));

        assertThat(bar.getId(), is(notNullValue()));
        bars.add(bar.getId());
        assertThat(bar.getVersion(), is(storedVersion(bar.getId())));

        final String inserted = bar.getVersion();

        inTransaction(writer -> {
            bar.setName("Writer #1 renamed");
            writer.update(bar, BAR.NAME);
            bar.setNum(2);
            writer.update(bar, BAR.NUM);
        });

        assertThat(dslContext.fetchOne(BAR, BAR.ID.eq(bar.getId())).getName(), is("Writer #1 renamed"));
        assertThat(dslContext.fetchOne(BAR, BAR.ID.eq(bar.getId())).getNum(), is(2));
        assertThat(bar.getVersion(), is(storedVersion(bar.getId())));
        assertThat(bar.getVersion(), is(not(inserted)));
        assertThat(dslContext.fetchCount(APP_VERSION, APP_VERSION.ENTITY_ID.eq(bar.getId())), is(3));
    }


    /// The writes join the caller's transaction, so a rollback takes them back.
    @Test
    void rollsBackWithTheCaller()
    {
        final Bar bar = newBar("Writer #2");

        assertThrows(IllegalStateException.class, () -> inTransaction(writer -> {
            writer.insert(bar);
            bars.add(bar.getId());
            throw new IllegalStateException("the caller fails");
        }));

        assertThat(dslContext.fetchCount(BAR, BAR.ID.eq(bar.getId())), is(0));
        assertThat(dslContext.fetchCount(APP_VERSION, APP_VERSION.ENTITY_ID.eq(bar.getId())), is(0));
    }


    @Test
    void requiresATransaction()
    {
        assertThrows(IllegalTransactionStateException.class, () -> entityWriter.insert(newBar("Writer #3")));
    }


    /// A row read before somebody else wrote it: where their write touched other fields, the update goes in over
    /// it, as the client's merge does.
    @Test
    void mergesOverAWriteToOtherFields()
    {
        final Bar bar = inserted(newBar("Writer #4"));
        final Bar stale = new Bar(bar);

        inTransaction(writer -> {
            bar.setName("Writer #4 renamed");
            writer.update(bar, BAR.NAME);
        });

        inTransaction(writer -> {
            stale.setNum(4);
            writer.update(stale, BAR.NUM);
        });

        final var stored = dslContext.fetchOne(BAR, BAR.ID.eq(bar.getId()));
        assertThat(stored.getName(), is("Writer #4 renamed"));
        assertThat(stored.getNum(), is(4));
    }


    /// Where both writes touched the same field it is a conflict, which throws and rolls the caller back.
    @Test
    void throwsOnAConflict()
    {
        final Bar bar = inserted(newBar("Writer #5"));
        final Bar stale = new Bar(bar);

        inTransaction(writer -> {
            bar.setName("Writer #5 renamed");
            writer.update(bar, BAR.NAME);
        });

        final MergeConflictException e = assertThrows(MergeConflictException.class, () -> inTransaction(writer -> {
            stale.setName("Writer #5 mine");
            writer.update(stale, BAR.NAME);
        }));

        final List<MergeConflict> conflicts = e.getConflicts();
        assertThat(conflicts, hasSize(1));
        assertThat(conflicts.get(0).getId(), is(bar.getId()));
        assertThat(dslContext.fetchOne(BAR, BAR.ID.eq(bar.getId())).getName(), is("Writer #5 renamed"));
    }


    @Test
    void deletesARow()
    {
        final Bar bar = inserted(newBar("Writer #6"));

        inTransaction(writer -> writer.delete(bar));

        assertThat(dslContext.fetchCount(BAR, BAR.ID.eq(bar.getId())), is(0));
    }


    @Test
    void linksAndUnlinksRows()
    {
        final Bar bar = inserted(newBar("Writer #7"));
        final Baz first = insertedBaz("Writer baz #1");
        final Baz second = insertedBaz("Writer baz #2");

        inTransaction(writer -> writer.link(bar, "bazes", first, second));
        assertThat(linkedBazs(bar), hasSize(2));

        inTransaction(writer -> writer.unlink(bar, "bazes", first));
        assertThat(linkedBazs(bar), contains(second.getId()));
    }


    /// A write is recorded for whoever the security context holds: the system user for work nobody started.
    @Test
    void recordsTheWriteForTheIdentityItRunsAs()
    {
        final Bar bar = newBar("Writer #8");

        AppAuthentication.runAs(AppAuthentication.SYSTEM_AUTH, () -> inTransaction(writer -> writer.insert(bar)));
        bars.add(bar.getId());

        assertThat(
            dslContext.select(APP_VERSION.OWNER_ID).from(APP_VERSION)
                .where(APP_VERSION.ENTITY_ID.eq(bar.getId()))
                .fetchOne(APP_VERSION.OWNER_ID),
            is(AppAuthentication.SYSTEM_ID)
        );
        assertThat(AppAuthentication.current().getLogin(), is(AppAuthentication.ANONYMOUS));
    }


    private void inTransaction(Consumer<EntityWriter> work)
    {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.accept(entityWriter));
    }


    private Bar inserted(Bar bar)
    {
        inTransaction(writer -> writer.insert(bar));
        bars.add(bar.getId());
        return bar;
    }


    private Baz insertedBaz(String name)
    {
        final Baz baz = new Baz();
        baz.setName(name);
        baz.setNum(1);
        baz.setCreated(Timestamp.valueOf("2026-10-23 12:00:00"));

        inTransaction(writer -> writer.insert(baz));
        bazs.add(baz.getId());
        return baz;
    }


    private static Bar newBar(String name)
    {
        final Bar bar = new Bar();
        bar.setName(name);
        bar.setNum(1);
        bar.setCreated(Timestamp.valueOf("2026-10-23 12:00:00"));
        return bar;
    }


    private List<String> linkedBazs(Bar bar)
    {
        return dslContext.select(BAR_LINK.BAZ_ID).from(BAR_LINK)
            .where(BAR_LINK.BAR_ID.eq(bar.getId()))
            .fetch(BAR_LINK.BAZ_ID);
    }


    private String storedVersion(String id)
    {
        return dslContext.select(BAR.VERSION).from(BAR).where(BAR.ID.eq(id)).fetchOne(BAR.VERSION);
    }
}
