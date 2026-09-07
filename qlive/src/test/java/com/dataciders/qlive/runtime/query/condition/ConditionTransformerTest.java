package com.dataciders.qlive.runtime.query.condition;

import com.dataciders.qlive.model.condition.CNode;
import com.dataciders.qlive.runtime.QLiveException;
import com.dataciders.qlive.runtime.scalar.ComputedValue;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.dataciders.qlive.runtime.scalar.FilterDSL.and;
import static com.dataciders.qlive.runtime.scalar.FilterDSL.component;
import static com.dataciders.qlive.runtime.scalar.FilterDSL.field;
import static com.dataciders.qlive.runtime.scalar.FilterDSL.value;
import static com.dataciders.qlive.runtime.scalar.FilterDSL.values;
import static com.dataciders.qlive.testdomain.Tables.TEST_FOO;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Covers the FilterDSL to JOOQ translation on its own, which is all it needs: the transformer knows
/// nothing about query documents, only about a resolver that says what a path means.
class ConditionTransformerTest
{
    private final static DSLContext ctx = DSL.using(SQLDialect.POSTGRES);

    /// Resolves a path to the column of that name on one table, which is all a caller filtering a single
    /// table needs and all these tests need.
    private final ConditionTransformer transformer = new ConditionTransformer(
        path -> new ResolvedField(TEST_FOO.field(path))
    );


    @Test
    void transformsComparisons()
    {
        assertThat(sql(field("name").eq(value("x"))), is("\"public\".\"test_foo\".\"name\" = 'x'"));
        assertThat(sql(field("num").gt(value(3))), is("\"public\".\"test_foo\".\"num\" > 3"));
        assertThat(sql(field("name").isNull()), is("\"public\".\"test_foo\".\"name\" is null"));
        assertThat(sql(field("num").between(value(1), value(5))), is("\"public\".\"test_foo\".\"num\" between 1 and 5"));
        assertThat(sql(field("num").in(values(List.of(1, 2, 3), "Int"))), is("\"public\".\"test_foo\".\"num\" in (1, 2, 3)"));
        assertThat(sql(field("name").containsIgnoreCase(value("x"))), containsString("ilike"));
    }


    @Test
    void transformsValueOperations()
    {
        assertThat(sql(field("num").plus(value(1)).eq(value(3))), is("(\"public\".\"test_foo\".\"num\" + 1) = 3"));
        assertThat(sql(field("name").lower().eq(value("x"))), is("lower(\"public\".\"test_foo\".\"name\") = 'x'"));
    }


    @Test
    void transformsLogicOperators()
    {
        assertThat(
            sql(and(field("name").eq(value("x")), field("num").gt(value(3)))),
            is("(\"public\".\"test_foo\".\"name\" = 'x' and \"public\".\"test_foo\".\"num\" > 3)")
        );
        assertThat(
            sql(field("name").eq(value("x")).or(field("num").gt(value(3)))),
            is("(\"public\".\"test_foo\".\"name\" = 'x' or \"public\".\"test_foo\".\"num\" > 3)")
        );
        assertThat(sql(field("name").eq(value("x")).not()), is("not (\"public\".\"test_foo\".\"name\" = 'x')"));
    }


    /// The client writes a null operand for a filter component that currently filters nothing, and a
    /// condition made only of those constrains nothing at all.
    @Test
    void dropsOperandsThatConstrainNothing()
    {
        assertThat(sql(and(field("name").eq(value("x")), null)), is("\"public\".\"test_foo\".\"name\" = 'x'"));
        assertThat(transformer.transform(null), is(nullValue()));
        assertThat(transformer.transform(and()), is(nullValue()));
    }


    /// A component is the client's marker for which part of a form a condition came from. The database has
    /// no use for it.
    @Test
    void unwrapsComponents()
    {
        assertThat(
            sql(component("c1", field("name").eq(value("x")))),
            is("\"public\".\"test_foo\".\"name\" = 'x'")
        );
    }


    /// now() and today() are the database's, so that every row of a query sees the same one.
    @Test
    void evaluatesComputedValuesInTheDatabase()
    {
        assertThat(
            sql(field("created").lt(value(new ComputedValue("now", List.of()), "ComputedValue"))),
            is("\"public\".\"test_foo\".\"created\" < current_timestamp")
        );
        assertThat(
            sql(field("created").lt(value(new ComputedValue("today", List.of()), "ComputedValue"))),
            is("\"public\".\"test_foo\".\"created\" < current_date")
        );

        assertThrows(
            QLiveException.class,
            () -> sql(field("created").lt(value(new ComputedValue("yesterday", List.of()), "ComputedValue")))
        );
    }


    @Test
    void sortsAscendingByDefault()
    {
        assertThat(ctx.renderInlined(transformer.sortField(field("name"))), is("\"public\".\"test_foo\".\"name\" asc"));
        assertThat(
            ctx.renderInlined(transformer.sortField(field("name").desc())),
            is("\"public\".\"test_foo\".\"name\" desc")
        );
    }


    /// Conditions arrive from a browser, so the operator name is checked before it is used for anything.
    @Test
    void rejectsOperatorsOutsideThePositiveList()
    {
        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> transformer.transform(condition("dropTable", field("name")))
        );
        assertThat(e.getMessage(), containsString("Invalid filter operator"));

        assertThrows(
            QLiveException.class,
            () -> transformer.transform(condition("toString", field("name")))
        );
    }


    /// On the list, but not with that many operands.
    @Test
    void rejectsOperatorsUsedWithTheWrongNumberOfOperands()
    {
        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> transformer.transform(condition("eq", field("name")))
        );
        assertThat(e.getMessage(), containsString("does not take 0 operand(s)"));
    }


    @Test
    void rejectsValuesWhereAConditionBelongs()
    {
        assertThrows(QLiveException.class, () -> transformer.transform(field("name")));
        assertThrows(QLiveException.class, () -> transformer.transform(value("x")));
    }


    /// A condition node with a name of our choosing, which the FilterDSL itself will not produce.
    private static com.dataciders.qlive.model.condition.Condition condition(String name, CNode... operands)
    {
        final com.dataciders.qlive.model.condition.Condition condition =
            new com.dataciders.qlive.model.condition.Condition();
        condition.setName(name);
        condition.setOperands(new ArrayList<>(Arrays.asList(operands)));
        return condition;
    }


    private String sql(CNode node)
    {
        final Condition condition = transformer.transform(node);
        return ctx.renderInlined(condition);
    }
}
