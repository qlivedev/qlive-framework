package io.github.qlivedev.runtime.query;

import org.jooq.DSLContext;
import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;
import org.jooq.Field;
import org.jooq.Query;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.Select;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/// A database that answers each statement with the rows the test said it would, in the order the statements
/// arrive, and keeps what it was asked.
///
/// A row names its columns the way the statement does, by table alias and column -- `"owner.login"` -- so
/// a test states what a database would have found without depending on the order the columns are selected
/// in. A column the row does not name is null; a statement nothing was scripted for finds nothing.
final class ScriptedDatabase
{
    /// One statement as it reached the database.
    record Statement(
        String sql,
        List<Object> bindings
    )
    {
    }


    private final Deque<List<Map<String, Object>>> answers = new ArrayDeque<>();

    private final List<Statement> statements = new ArrayList<>();

    /// The query being executed, which is the only place its select list can be read from: the mock
    /// connection is handed the SQL and nothing else.
    private Query current;


    /// Scripts the rows the next unanswered statement finds.
    @SafeVarargs
    final ScriptedDatabase answer(Map<String, Object>... rows)
    {
        answers.add(List.of(rows));
        return this;
    }


    List<Statement> statements()
    {
        return statements;
    }


    List<String> sql()
    {
        return statements.stream().map(Statement::sql).toList();
    }


    DSLContext dslContext()
    {
        return DSL.using(
            new DefaultConfiguration()
                .set(SQLDialect.POSTGRES)
                .set(new MockConnection(ctx -> {
                    statements.add(new Statement(ctx.sql(), Arrays.asList(ctx.bindings())));
                    return new MockResult[]{ new MockResult(0, result()) };
                }))
                .set(new ExecuteListener()
                {
                    @Override
                    public void executeStart(ExecuteContext ctx)
                    {
                        current = ctx.query();
                    }
                })
        );
    }


    private Result<Record> result()
    {
        final DSLContext create = DSL.using(SQLDialect.POSTGRES);

        if (!(current instanceof Select<?> select))
        {
            return create.newResult();
        }

        final List<Field<?>> fields = select.getSelect();
        final Result<Record> result = create.newResult(fields);

        for (Map<String, Object> row : answers.isEmpty() ? List.<Map<String, Object>>of() : answers.poll())
        {
            final Record record = create.newRecord(fields);
            record.fromArray(fields.stream().map(field -> row.get(name(field))).toArray());
            result.add(record);
        }

        return result;
    }


    /// `alias.column` for a column, and the plain name for anything else -- `count` for a count.
    private static String name(Field<?> field)
    {
        return String.join(".", field.getQualifiedName().getName());
    }
}
