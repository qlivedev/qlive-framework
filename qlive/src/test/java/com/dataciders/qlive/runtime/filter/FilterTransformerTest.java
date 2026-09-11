package com.dataciders.qlive.runtime.filter;

import com.dataciders.qlive.model.condition.CNode;
import com.dataciders.qlive.model.condition.Condition;
import com.dataciders.qlive.runtime.QLiveException;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static com.dataciders.qlive.runtime.scalar.FilterDSL.and;
import static com.dataciders.qlive.runtime.scalar.FilterDSL.field;
import static com.dataciders.qlive.runtime.scalar.FilterDSL.not;
import static com.dataciders.qlive.runtime.scalar.FilterDSL.or;
import static com.dataciders.qlive.runtime.scalar.FilterDSL.value;
import static com.dataciders.qlive.runtime.scalar.FilterDSL.values;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Covers the evaluator on its own: no socket, no registry, no Spring context. A condition and a payload
/// class go in, a predicate comes out, and everything the condition alone can get wrong is refused while
/// the caller compiling it is still there to hear about it.
class FilterTransformerTest
{
    private final static Timestamp CREATED = Timestamp.from(Instant.parse("2026-09-10T12:00:00.000Z"));

    /// Bit 65 set, which is past what a long holds and the reason field masks travel as BigInteger.
    private final static BigInteger HIGH_BIT = BigInteger.ONE.shiftLeft(65);


    // -----------------------------------------------------------------------------------------------------
    // flat payloads, and the operator table
    // -----------------------------------------------------------------------------------------------------

    @Test
    void matchesOnAFlatPayload()
    {
        final Predicate<Object> filter = compile(field("entityType").eq(value("Bar")));

        assertThat(filter.test(version("Bar", "u1", BigInteger.ONE)), is(true));
        assertThat(filter.test(version("Baz", "u1", BigInteger.ONE)), is(false));
    }


    @Test
    void combinesConditionsWithLogic()
    {
        final VersionPayload bar = version("Bar", "u1", BigInteger.ONE);
        final VersionPayload baz = version("Baz", "u2", BigInteger.ONE);

        assertThat(
            compile(and(field("entityType").eq(value("Bar")), field("ownerId").eq(value("u1")))).test(bar),
            is(true)
        );
        assertThat(
            compile(and(field("entityType").eq(value("Bar")), field("ownerId").eq(value("u2")))).test(bar),
            is(false)
        );
        assertThat(
            compile(or(field("entityType").eq(value("Bar")), field("entityType").eq(value("Baz")))).test(baz),
            is(true)
        );
        assertThat(compile(not(field("entityType").eq(value("Bar")))).test(baz), is(true));
        assertThat(
            compile(field("entityType").eq(value("Bar")).andNot(field("ownerId").eq(value("u2")))).test(bar),
            is(true)
        );
        assertThat(
            compile(field("entityType").eq(value("Baz")).orNot(field("ownerId").eq(value("u2")))).test(bar),
            is(true)
        );
    }


    /// A condition's constants are whatever JSON and the scalar coercing made of them, and a payload's
    /// fields are whatever the publisher declared. Neither side gets to decide what the other must be.
    @Test
    void comparesNumbersByValueAcrossTheirClasses()
    {
        final VersionPayload payload = version("Bar", "u1", BigInteger.ONE);

        assertThat(compile(field("revision").eq(value(7L))).test(payload), is(true));
        assertThat(compile(field("revision").eq(value(7))).test(payload), is(true));
        assertThat(compile(field("revision").gt(value(6L))).test(payload), is(true));
        assertThat(compile(field("revision").le(value(7L))).test(payload), is(true));
        assertThat(compile(field("revision").lt(value(7L))).test(payload), is(false));
        assertThat(compile(field("revision").between(value(1), value(10))).test(payload), is(true));
    }


    /// The motivating subscription: tell me about changes that touched a field this form shows, and were
    /// not made by me. The mask is 128 bits wide, so the arithmetic has to be exact past a long.
    @Test
    void masksWithBigIntegers()
    {
        final CNode shown = field("fieldMask").bitAnd(big(HIGH_BIT.or(BigInteger.valueOf(4)))).ne(value(0));

        final CNode condition = and(
            (Condition) field("entityType").eq(value("Bar")),
            (Condition) shown,
            (Condition) field("ownerId").ne(value("me"))
        );

        final Predicate<Object> filter = compile(condition);

        assertThat(filter.test(version("Bar", "somebody", HIGH_BIT)), is(true));
        assertThat(filter.test(version("Bar", "somebody", BigInteger.valueOf(4))), is(true));
        assertThat(filter.test(version("Bar", "somebody", BigInteger.valueOf(8))), is(false));
        assertThat(filter.test(version("Bar", "me", HIGH_BIT)), is(false));
        assertThat(filter.test(version("Baz", "somebody", HIGH_BIT)), is(false));
    }


    /// A payload legitimately having nothing at a path is ordinary, and a comparison against it simply
    /// does not match -- including the negative ones, which is where this differs from asking a database.
    @Test
    void doesNotMatchWhereThePayloadHasNothing()
    {
        final VersionPayload payload = new VersionPayload("Bar", "42", null, null, null, null, null);

        assertThat(compile(field("ownerId").eq(value("me"))).test(payload), is(false));
        assertThat(compile(field("ownerId").ne(value("me"))).test(payload), is(false));
        assertThat(compile(field("fieldMask").bitAnd(value(4)).ne(value(0))).test(payload), is(false));
        assertThat(compile(field("ownerId").isNull()).test(payload), is(true));
        assertThat(compile(field("ownerId").isNotNull()).test(payload), is(false));
    }


    @Test
    void coversTheRestOfTheOperatorTable()
    {
        final VersionPayload payload = version("Bar", "u1", BigInteger.ONE);

        assertThat(compile(field("entityId").in(values(List.of("41", "42"), "String"))).test(payload), is(true));
        assertThat(compile(field("entityId").in(values(List.of("41"), "String"))).test(payload), is(false));
        assertThat(compile(field("entityType").startsWith(value("Ba"))).test(payload), is(true));
        assertThat(compile(field("entityType").endsWith(value("ar"))).test(payload), is(true));
        assertThat(compile(field("entityType").contains(value("a"))).test(payload), is(true));
        assertThat(compile(field("entityType").containsIgnoreCase(value("BAR"))).test(payload), is(true));
        assertThat(compile(field("entityType").equalIgnoreCase(value("bar"))).test(payload), is(true));
        assertThat(compile(field("entityType").likeRegex(value("^B.r$"))).test(payload), is(true));
        assertThat(compile(field("entityType").likeRegex(value("a"))).test(payload), is(true));
        assertThat(compile(field("entityType").notLikeRegex(value("z"))).test(payload), is(true));
        assertThat(compile(field("entityType").lower().eq(value("bar"))).test(payload), is(true));
        assertThat(compile(field("entityType").upper().eq(value("BAR"))).test(payload), is(true));
        assertThat(compile(field("draft").isTrue()).test(payload), is(true));
        assertThat(compile(field("draft").isFalse()).test(payload), is(false));
        assertThat(compile(field("revision").add(value(3)).eq(value(10))).test(payload), is(true));
        assertThat(compile(field("created").lt(value(Timestamp.from(Instant.now())))).test(payload), is(true));
    }


    /// A condition with nothing left in it constrains nothing, which a subscription reads as "everything
    /// on this channel" rather than as "nothing".
    @Test
    void compilesAnEmptyConditionToNothing()
    {
        assertThat(new FilterTransformer(VersionPayload.class).transform(null), is(nullValue()));
    }


    // -----------------------------------------------------------------------------------------------------
    // what is refused, and when
    // -----------------------------------------------------------------------------------------------------

    /// An operator with no meaning off a database is refused while the subscriber is still listening, not
    /// turned into a filter that never matches anything for the rest of the connection's life.
    @Test
    void refusesAnOperatorItCannotHonour()
    {
        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> compile(field("entityType").isDistinctFrom(value("Bar")))
        );

        assertThat(e.getMessage(), containsString("isDistinctFrom"));
    }


    @Test
    void refusesAFieldTheChannelDoesNotHave()
    {
        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> compile(field("nothingLikeThis").eq(value("Bar")))
        );

        assertThat(e.getMessage(), containsString("nothingLikeThis"));
        assertThat(e.getMessage(), containsString(VersionPayload.class.getName()));
    }


    @Test
    void refusesAnOperatorGivenTheWrongNumberOfOperands()
    {
        final Condition condition = new Condition();
        condition.setName("eq");
        condition.setOperands(List.of(field("entityType"), value("a"), value("b")));

        assertThrows(QLiveException.class, () -> compile(condition));
    }


    /// A channel whose payloads have no declared shape has nothing to check a path against, so paths are
    /// taken as written and resolved against whatever arrives. The same read works on a map and on a bean.
    @Test
    void takesPathsAsWrittenWithoutAPayloadClass()
    {
        final Predicate<Object> filter = new FilterTransformer(null)
            .transform(field("entityType").eq(value("Bar")));

        assertThat(filter.test(Map.of("entityType", "Bar")), is(true));
        assertThat(filter.test(Map.of("entityType", "Baz")), is(false));
        assertThat(filter.test(version("Bar", "u1", BigInteger.ONE)), is(true));
    }


    // -----------------------------------------------------------------------------------------------------
    // relations, which are ordinary properties and nothing more
    // -----------------------------------------------------------------------------------------------------

    /// A relation in a payload is a real getter holding real objects. Nothing resolves a GraphQL field
    /// here and nothing queries: a relation the publisher left unpopulated just does not match.
    @Test
    void readsARelationAsAPlainProperty()
    {
        final Predicate<Object> filter = new FilterTransformer(BarPayload.class)
            .transform(field("bazLinks.0.baz.name").eq(value("Baz #1")));

        assertThat(filter.test(bar(link("Baz #1"))), is(true));
        assertThat(filter.test(bar(link("Baz #9"))), is(false));
        assertThat(filter.test(bar()), is(false));
        assertThat(filter.test(new BarPayload("Bar #1", null)), is(false));
    }


    /// A to-many hop is an index into a dead data tree, the way any data pointer works -- not the SQL
    /// backend's "does some element satisfy the rest of the path".
    @Test
    void addressesAToManyHopByIndex()
    {
        final BarPayload payload = bar(link("Baz #1"), link("Baz #2"));

        assertThat(
            new FilterTransformer(BarPayload.class)
                .transform(field("bazLinks.0.baz.name").eq(value("Baz #2")))
                .test(payload),
            is(false)
        );
        assertThat(
            new FilterTransformer(BarPayload.class)
                .transform(field("bazLinks.1.baz.name").eq(value("Baz #2")))
                .test(payload),
            is(true)
        );
        assertThat(
            new FilterTransformer(BarPayload.class)
                .transform(field("bazLinks.2.baz.name").eq(value("Baz #2")))
                .test(payload),
            is(false)
        );
    }


    /// Which is also why a path that crosses one without saying which element is refused rather than
    /// quietly meaning the first.
    @Test
    void refusesAToManyHopWithoutAnIndex()
    {
        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> new FilterTransformer(BarPayload.class).transform(field("bazLinks.baz.name").eq(value("Baz #1")))
        );

        assertThat(e.getMessage(), containsString("bazLinks.baz.name"));
    }


    // -----------------------------------------------------------------------------------------------------

    private Predicate<Object> compile(CNode condition)
    {
        return new FilterTransformer(VersionPayload.class).transform(condition);
    }


    private static com.dataciders.qlive.model.condition.Value big(BigInteger value)
    {
        return value(value, "BigInteger");
    }


    private static VersionPayload version(String entityType, String ownerId, BigInteger fieldMask)
    {
        return new VersionPayload(entityType, "42", fieldMask, ownerId, CREATED, 7, true);
    }


    private static BarPayload bar(BazLinkPayload... links)
    {
        return new BarPayload("Bar #1", List.of(links));
    }


    private static BazLinkPayload link(String bazName)
    {
        return new BazLinkPayload(new BazPayload(bazName));
    }


    /// A channel payload of the shape entity-version push publishes: flat, no relations, plain properties.
    public static class VersionPayload
    {
        private final String entityType;
        private final String entityId;
        private final BigInteger fieldMask;
        private final String ownerId;
        private final Timestamp created;
        private final Integer revision;
        private final Boolean draft;


        VersionPayload(
            String entityType,
            String entityId,
            BigInteger fieldMask,
            String ownerId,
            Timestamp created,
            Integer revision,
            Boolean draft
        )
        {
            this.entityType = entityType;
            this.entityId = entityId;
            this.fieldMask = fieldMask;
            this.ownerId = ownerId;
            this.created = created;
            this.revision = revision;
            this.draft = draft;
        }


        public String getEntityType() { return entityType; }
        public String getEntityId() { return entityId; }
        public BigInteger getFieldMask() { return fieldMask; }
        public String getOwnerId() { return ownerId; }
        public Timestamp getCreated() { return created; }
        public Integer getRevision() { return revision; }
        public Boolean getDraft() { return draft; }
    }


    /// A payload a publisher built to carry a relation: an ordinary class with an ordinary getter, which
    /// is the only way a relation ever reaches a subscriber's condition.
    public static class BarPayload
    {
        private final String name;
        private final List<BazLinkPayload> bazLinks;


        BarPayload(String name, List<BazLinkPayload> bazLinks)
        {
            this.name = name;
            this.bazLinks = bazLinks;
        }


        public String getName() { return name; }
        public List<BazLinkPayload> getBazLinks() { return bazLinks; }
    }


    public static class BazLinkPayload
    {
        private final BazPayload baz;


        BazLinkPayload(BazPayload baz) { this.baz = baz; }


        public BazPayload getBaz() { return baz; }
    }


    public static class BazPayload
    {
        private final String name;


        BazPayload(String name) { this.name = name; }


        public String getName() { return name; }
    }
}
