package io.github.qlivedev.qlivetest.runtime;

import io.github.qlivedev.qlive.api.generic.GenericScalar;
import io.github.qlivedev.qlive.model.merge.EntityChange;
import io.github.qlivedev.qlive.model.merge.FieldChange;
import io.github.qlivedev.qlive.model.merge.MergeConfig;
import io.github.qlivedev.qlive.model.merge.MergeStatus;
import io.github.qlivedev.qlive.runtime.merge.EntityWriter;
import io.github.qlivedev.qlive.runtime.merge.MergeService;
import io.github.qlivedev.qlivetest.domain.tables.pojos.Bar;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.github.qlivedev.qlivetest.domain.Tables.APP_VERSION;
import static io.github.qlivedev.qlivetest.domain.Tables.BAR;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/// An action whose own write meets a conflict, after its subjects were loaded and found unchanged. Either way it
/// is refused like a subject that changed before, and nothing it wrote stays.
///
/// The `EntityWriter` is a spy, which gives this class a Spring context of its own.
@SpringBootTest
class ActionConflictTest
{
    private final static String APPROVE_BAR =
        "mutation approve($bar: SubjectRefInput!, $comment: String!, $notify: Boolean!) {" +
            "  approveBar(bar: $bar, comment: $comment, notify: $notify) { barId }" +
            "}";

    @Autowired
    private GraphQL graphQL;

    @Autowired
    private MergeService mergeService;

    @Autowired
    private DSLContext dslContext;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    private EntityWriter entityWriter;

    /// Ids of the bars this test made, for the cleanup that has to run either way.
    private final List<String> bars = new ArrayList<>();


    @AfterEach
    void removeWhatWasMade()
    {
        dslContext.deleteFrom(BAR).where(BAR.ID.in(bars)).execute();
        dslContext.deleteFrom(APP_VERSION).where(APP_VERSION.ENTITY_ID.in(bars)).execute();
    }


    /// Somebody else's merge of the row is open while the action loads it, so the action sees the version the
    /// user saw, and its write waits for the row. When that merge commits, the database refuses the write, and
    /// there is no field of the action's to blame.
    @Test
    void refusesAWriteAConcurrentMergeGotInFrontOf()
        throws Exception
    {
        final String id = newBar("Conflict #1");
        final String seen = version(id);

        final CountDownLatch written = new CountDownLatch(1);
        final CountDownLatch commit = new CountDownLatch(1);

        final CompletableFuture<Void> concurrent = CompletableFuture.runAsync(
            () -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                final EntityChange change = change(id, seen, field("name", "String", "Conflict #1 renamed"));
                assertThat(
                    mergeService.mergeInTransaction(List.of(change), List.of(), List.of(), new MergeConfig())
                        .getStatus(),
                    is(MergeStatus.DONE)
                );
                written.countDown();
                await(commit);
            })
        );
        await(written);

        final CompletableFuture<ExecutionResult> action = CompletableFuture.supplyAsync(
            () -> execute(Map.of("bar", ref(id, seen), "comment", "fine", "notify", false))
        );

        awaitLockWait();
        commit.countDown();
        concurrent.get(10, TimeUnit.SECONDS);

        final ExecutionResult result = action.get(10, TimeUnit.SECONDS);

        assertThat(result.getData(), is(nullValue()));
        assertThat(
            result.getErrors().stream().map(e -> e.getExtensions()).toList(),
            contains(refusal("Action.subjectChanged", null))
        );
        assertThat(approved(id), is(false));
        assertThat(name(id), is("Conflict #1 renamed"));
    }


    /// The method wrote the row before, from a copy of its own, and then writes the subject it was handed, whose
    /// version that write left behind. The conflict blames the subject.
    @Test
    void refusesAWriteThatConflictsWithinTheAction()
    {
        final String id = newBar("Conflict #2");

        final AtomicBoolean interfered = new AtomicBoolean();
        doAnswer(invocation -> {
            if (!interfered.getAndSet(true))
            {
                final Bar bar = invocation.getArgument(0);
                final Bar earlier = dslContext.selectFrom(BAR).where(BAR.ID.eq(bar.getId())).fetchOneInto(Bar.class);
                earlier.setApproved(Timestamp.valueOf("2026-10-01 12:00:00"));
                entityWriter.update(earlier, BAR.APPROVED);
            }
            return invocation.callRealMethod();
        }).when(entityWriter).update(any(Bar.class), any(Field[].class));

        final ExecutionResult result = execute(Map.of("bar", ref(id, version(id)), "comment", "fine", "notify", false));

        assertThat(interfered.get(), is(true));
        assertThat(result.getData(), is(nullValue()));
        assertThat(
            result.getErrors().stream().map(e -> e.getExtensions()).toList(),
            contains(refusal("Action.subjectChanged", "bar"))
        );
        // the earlier write went with the refused action
        assertThat(approved(id), is(false));
    }


    // -----------------------------------------------------------------------------------------------------

    private ExecutionResult execute(Map<String, Object> variables)
    {
        return graphQL.execute(ExecutionInput.newExecutionInput().query(APPROVE_BAR).variables(variables).build());
    }


    /// Waits until a statement of this database waits for a lock, which is the action's write waiting for the
    /// row the concurrent merge holds.
    private void awaitLockWait()
        throws InterruptedException
    {
        final long deadline = System.currentTimeMillis() + 10_000;
        while (dslContext.fetchCount(
            dslContext.selectOne()
                .from("pg_stat_activity")
                .where("wait_event_type = 'Lock' and datname = current_database()")
        ) == 0)
        {
            if (System.currentTimeMillis() > deadline)
            {
                throw new AssertionError("The action's write never waited for the row");
            }
            Thread.sleep(10);
        }
    }


    private static void await(CountDownLatch latch)
    {
        try
        {
            if (!latch.await(10, TimeUnit.SECONDS))
            {
                throw new AssertionError("Timed out");
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }


    private static Map<String, Object> refusal(String messageKey, String field)
    {
        final Map<String, Object> map = new HashMap<>();
        map.put("code", "CONFLICT");
        map.put("messageKey", messageKey);
        map.put("field", field);
        map.put("errorClass", null);
        return map;
    }


    private static Map<String, Object> ref(String id, String version)
    {
        return Map.of("id", id, "version", version);
    }


    private String newBar(String name)
    {
        final String id = UUID.randomUUID().toString();
        bars.add(id);

        final EntityChange change = change(
            id,
            null,
            field("name", "String", name),
            field("description", "String", "described"),
            field("num", "Int", 1),
            field("created", "Timestamp", Timestamp.valueOf("2026-10-23 12:00:00"))
        );
        change.setNew(true);

        assertThat(
            mergeService.merge(List.of(change), List.of(), List.of(), new MergeConfig()).getStatus(),
            is(MergeStatus.DONE)
        );
        return id;
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


    private boolean approved(String id)
    {
        return dslContext.select(BAR.APPROVED).from(BAR).where(BAR.ID.eq(id)).fetchOne(BAR.APPROVED) != null;
    }


    private String name(String id)
    {
        return dslContext.select(BAR.NAME).from(BAR).where(BAR.ID.eq(id)).fetchOne(BAR.NAME);
    }


    private String version(String id)
    {
        return dslContext.select(BAR.VERSION).from(BAR).where(BAR.ID.eq(id)).fetchOne(BAR.VERSION);
    }


    private static FieldChange field(String name, String scalarType, Object value)
    {
        final FieldChange change = new FieldChange();
        change.setField(name);
        change.setValue(new GenericScalar(scalarType, value));
        return change;
    }
}
