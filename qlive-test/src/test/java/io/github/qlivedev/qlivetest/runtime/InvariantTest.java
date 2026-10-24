package io.github.qlivedev.qlivetest.runtime;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.api.annotation.Rules;
import io.github.qlivedev.qlive.api.generic.GenericScalar;
import io.github.qlivedev.qlive.graphql.QLiveDomain;
import io.github.qlivedev.qlive.graphql.config.SourceField;
import io.github.qlivedev.qlive.graphql.config.TargetField;
import io.github.qlivedev.qlive.model.merge.EntityChange;
import io.github.qlivedev.qlive.model.merge.FieldChange;
import io.github.qlivedev.qlive.model.merge.MergeConfig;
import io.github.qlivedev.qlive.model.merge.MergeRefusal;
import io.github.qlivedev.qlive.model.merge.MergeResult;
import io.github.qlivedev.qlive.model.merge.MergeStatus;
import io.github.qlivedev.qlive.runtime.QLiveException;
import io.github.qlivedev.qlive.runtime.action.Invariants;
import io.github.qlivedev.qlive.runtime.action.Rule;
import io.github.qlivedev.qlive.runtime.action.RuleContext;
import io.github.qlivedev.qlive.runtime.action.RuleFactory;
import io.github.qlivedev.qlive.runtime.domain.QLiveDefaultDomain;
import io.github.qlivedev.qlive.runtime.merge.EntityWriter;
import io.github.qlivedev.qlive.runtime.merge.MergeRefusedException;
import io.github.qlivedev.qlive.runtime.merge.MergeService;
import io.github.qlivedev.qlive.runtime.meta.RulesMetadataProvider;
import io.github.qlivedev.qlivetest.domain.Public;
import io.github.qlivedev.qlivetest.domain.tables.pojos.Foo;
import io.github.qlivedev.qlivetest.domain.tables.pojos.Quux;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.github.qlivedev.qlive.runtime.scalar.FilterDSL.value;
import static io.github.qlivedev.qlivetest.domain.Tables.FOO;
import static io.github.qlivedev.qlivetest.domain.Tables.QUUX;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// The invariants of a type, which hold for every write of its rows: Quux's numbers in order and its name given,
/// attached by the application's `RulesMetadataProvider`, checked by the merge on the row as a change sends it.
@SpringBootTest
class InvariantTest
{
    @Autowired
    private MergeService mergeService;

    @Autowired
    private EntityWriter entityWriter;

    @Autowired
    private QLiveDomain domain;

    @Autowired
    private DSLContext dslContext;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /// Ids of the rows this test made, for the cleanup that has to run either way.
    private final List<String> quuxes = new ArrayList<>();


    @AfterEach
    void removeWhatWasMade()
    {
        dslContext.deleteFrom(QUUX).where(QUUX.ID.in(quuxes)).execute();
    }


    /// The invariants land in the type meta data, which is how they reach the client, in the bootstrap.
    @Test
    void attachesTheInvariantsToTheType()
    {
        assertThat(
            Invariants.of(domain, "Quux").stream().map(Rule::getMessageKey).toList(),
            contains("Quux.numsOrdered", "Quux.nameRequired")
        );
        assertThat(Invariants.messageKeys(domain), hasItems("Quux.numsOrdered", "Quux.nameRequired"));
        assertThat(domain.getMetaData().toJSON(), containsString("\"messageKey\":\"Quux.numsOrdered\""));
    }


    @Test
    void writesARowThatKeepsTheInvariants()
    {
        final String id = newId();

        assertThat(merge(newQuux(id, "Invariant #1", 1, 2)).getStatus(), is(MergeStatus.DONE));
        assertThat(dslContext.fetchCount(QUUX, QUUX.ID.eq(id)), is(1));
    }


    /// Every broken rule is one refusal, blaming its field, and nothing is written.
    @Test
    void refusesANewRowBreakingThem()
    {
        final String id = newId();

        final MergeResult result = merge(newQuux(id, "", 3, 2));

        assertThat(result.getStatus(), is(MergeStatus.REFUSED));
        assertThat(result.getConflicts(), is(empty()));
        assertThat(
            result.getRefusals().stream().map(InvariantTest::describe).toList(),
            contains("Quux " + id + " numB Quux.numsOrdered", "Quux " + id + " name Quux.nameRequired")
        );
        assertThat(dslContext.fetchCount(QUUX, QUUX.ID.eq(id)), is(0));
    }


    /// A field a new row leaves out reads as null, and a comparison with null holds for nothing.
    @Test
    void refusesANewRowLeavingOutAField()
    {
        final String id = newId();

        final MergeResult result = merge(
            create(id, field("name", "String", "Invariant #3"), field("numA", "Int", 1), created())
        );

        assertThat(result.getStatus(), is(MergeStatus.REFUSED));
        assertThat(
            result.getRefusals().stream().map(InvariantTest::describe).toList(),
            contains("Quux " + id + " numB Quux.numsOrdered")
        );
    }


    /// An update is held to the invariants reading a field it changes; the others keep the outcome they had.
    @Test
    void checksOnlyTheInvariantsAnUpdateTouches()
    {
        final String id = newId();
        merge(newQuux(id, "Invariant #4", 1, 2));

        assertThat(merge(change(id, field("name", "String", "Invariant #4 renamed"))).getStatus(), is(MergeStatus.DONE));

        final MergeResult result = merge(change(id, field("numA", "Int", 5), field("numB", "Int", 2)));
        assertThat(result.getStatus(), is(MergeStatus.REFUSED));
        assertThat(
            result.getRefusals().stream().map(InvariantTest::describe).toList(),
            contains("Quux " + id + " numB Quux.numsOrdered")
        );
    }


    /// An update touching an invariant has to send every field it reads: the change is all the merge evaluates.
    @Test
    void failsAnUpdateLeavingOutAFieldTheInvariantReads()
    {
        final String id = newId();
        merge(newQuux(id, "Invariant #5", 1, 2));

        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> merge(change(id, field("numA", "Int", 2)))
        );
        assertThat(e.getMessage(), containsString("leaves out [numB]"));
    }


    /// The writer sends the fields an invariant reads besides the ones written, from the POJO, and a broken
    /// invariant is an exception that rolls the caller back.
    @Test
    void holdsTheEntityWriterToThem()
    {
        final String id = newId();
        merge(newQuux(id, "Invariant #6", 1, 2));
        final Quux quux = dslContext.fetchOne(QUUX, QUUX.ID.eq(id)).into(Quux.class);

        quux.setNumA(2);
        inTransaction(() -> entityWriter.update(quux, QUUX.NUM_A));
        assertThat(dslContext.fetchOne(QUUX, QUUX.ID.eq(id)).getNumA(), is(2));

        quux.setNumA(3);
        quux.setNumB(1);
        final MergeRefusedException e = assertThrows(
            MergeRefusedException.class,
            () -> inTransaction(() -> entityWriter.update(quux, QUUX.NUM_A, QUUX.NUM_B))
        );
        assertThat(e.getRefusals().stream().map(InvariantTest::describe).toList(), contains("Quux " + id + " numB Quux.numsOrdered"));
        assertThat(dslContext.fetchOne(QUUX, QUUX.ID.eq(id)).getNumA(), is(2));
    }


    /// A POJO the domain exposes names its invariants with `@Rules` on its class.
    @Test
    void readsTheInvariantsOfAnAnnotatedClass()
    {
        final QLiveDomain domain = domainWith(RulesMetadataProvider.newProvider(), new PeriodLogic());

        assertThat(
            Invariants.of(domain, "Period").stream().map(Rule::getMessageKey).toList(),
            contains("Period.ordered")
        );
    }


    /// The paths are checked at startup: a field of the type, without a parameter name, and no relation.
    @Test
    void refusesAPathTheTypeHasNoFieldFor()
    {
        final RuntimeException unknown = assertThrows(RuntimeException.class, () -> domainWith(
            RulesMetadataProvider.newProvider()
                .rules(Quux.class, ctx -> new Rule(ctx.field("quux.numA").gt(value(0)), null, "Quux.positive"))
        ));
        assertThat(unknown.getMessage(), containsString("Path 'quux.numA' names no field of 'Quux'"));

        final RuntimeException relation = assertThrows(RuntimeException.class, () -> domainWith(
            RulesMetadataProvider.newProvider()
                .rules(Foo.class, ctx -> new Rule(ctx.field("owner").isNotNull(), null, "Foo.owned"))
        ));
        assertThat(relation.getMessage(), containsString("Path 'owner' names a relation"));
    }


    // -----------------------------------------------------------------------------------------------------

    /// A period whose end is not before its start, the classic invariant over two fields.
    @Rules(EndNotBeforeStart.class)
    public static class Period
    {
        private Timestamp start;

        private Timestamp end;


        public Timestamp getStart()
        {
            return start;
        }


        public void setStart(Timestamp start)
        {
            this.start = start;
        }


        public Timestamp getEnd()
        {
            return end;
        }


        public void setEnd(Timestamp end)
        {
            this.end = end;
        }
    }


    public static class EndNotBeforeStart
        implements RuleFactory
    {
        @Override
        public Rule create(RuleContext ctx)
        {
            return new Rule(ctx.field("end").ge(ctx.field("start")), "end", "Period.ordered");
        }
    }


    /// What puts Period in the schema.
    @QLiveLogic
    public static class PeriodLogic
    {
        @QLiveQuery
        public Period period()
        {
            return new Period();
        }
    }


    /// A domain of the application's tables, with Foo's owner relation as the application configures it.
    private static QLiveDomain domainWith(RulesMetadataProvider provider, Object... logicBeans)
    {
        return QLiveDefaultDomain.newDomain(null, List.of(provider))
            .logicBeans(logicBeans)
            .objectTypes(Public.PUBLIC)
            .configureRelation(FOO.OWNER_ID, SourceField.OBJECT_AND_SCALAR, TargetField.MANY)
            .build();
    }


    private void inTransaction(Runnable work)
    {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }


    private MergeResult merge(EntityChange... changes)
    {
        return mergeService.merge(List.of(changes), List.of(), List.of(), new MergeConfig());
    }


    private static String describe(MergeRefusal refusal)
    {
        return refusal.getType() + " " + refusal.getId() + " " + refusal.getField() + " " + refusal.getMessageKey();
    }


    private static EntityChange newQuux(String id, String name, int numA, int numB)
    {
        return create(id, field("name", "String", name), field("numA", "Int", numA), field("numB", "Int", numB), created());
    }


    private static FieldChange created()
    {
        return field("created", "Timestamp", Timestamp.valueOf("2026-10-24 12:00:00"));
    }


    private static EntityChange create(String id, FieldChange... fields)
    {
        final EntityChange change = change(id, fields);
        change.setNew(true);
        return change;
    }


    private static EntityChange change(String id, FieldChange... fields)
    {
        final EntityChange change = new EntityChange();
        change.setType("Quux");
        change.setId(id);
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


    private String newId()
    {
        final String id = UUID.randomUUID().toString();
        quuxes.add(id);
        return id;
    }
}
