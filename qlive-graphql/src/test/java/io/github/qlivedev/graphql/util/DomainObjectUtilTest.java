package io.github.qlivedev.graphql.util;

import io.github.qlivedev.graphql.DomainQL;
import io.github.qlivedev.graphql.beans.SourceSeven;
import io.github.qlivedev.graphql.logicimpl.OutputTypeOverrideLogic;
import io.github.qlivedev.graphql.testdomain.Public;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * Covers the one thing about this class that is not jOOQ's: which of a domain object's properties become
 * column values.
 */
public class DomainObjectUtilTest
{
    private final List<String> statements = new ArrayList<>();

    private final MockDataProvider provider = ctx -> {
        statements.add(ctx.sql());
        return new MockResult[]{new MockResult(1, null)};
    };

    private final DSLContext dslContext = DSL.using(new MockConnection(provider), SQLDialect.POSTGRES);

    private final DomainQL domainQL = DomainQL.newDomainQL(dslContext)
        .objectTypes(Public.PUBLIC)
        .logicBeans(Collections.singleton(new OutputTypeOverrideLogic()))
        .build();


    /// `beans.SourceSeven` replaces the generated POJO and adds `getConcat()`, a computed field that no
    /// column backs. Storing the object writes the columns and passes that property over.
    ///
    /// Without the null check the lookup's null reaches `StoreQuery.addValue`, which does not complain:
    /// jOOQ renders the column as `"unknown field 0"` and the statement only fails once a database sees it.
    /// So the assertion is on the SQL, which is where the symptom is.
    @Test
    public void testComputedPropertyIsNotStored()
    {
        final SourceSeven sourceSeven = new SourceSeven();
        sourceSeven.setId("id-1");
        sourceSeven.setTarget("target-1");

        assertThat(sourceSeven.propertyNames().contains("concat"), is(true));
        assertThat(domainQL.lookupField("SourceSeven", "concat"), is(nullValue()));

        final int count = DomainObjectUtil.insert(dslContext, domainQL, sourceSeven);

        assertThat(count, is(1));
        assertThat(statements.size(), is(1));

        final String sql = statements.get(0);

        assertThat(sql, not(containsString("unknown field")));
        assertThat(sql, not(containsString("concat")));
        assertThat(sql, containsString("\"id\""));
        assertThat(sql, containsString("\"target\""));

        // the two columns and nothing else
        assertThat(sql, containsString("values (?, ?)"));
    }
}
