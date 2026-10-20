package io.github.qlivedev.qlivetest.runtime;

import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.runtime.query.QueryDocumentService;
import io.github.qlivedev.runtime.service.Catalogs;
import graphql.GraphQL;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static io.github.qlivedev.qlivetest.domain.Tables.FOO_TYPE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

/// Reads the catalog types of this application, FooType being the one.
///
/// What the catalog has to hold is read from the table itself, so the test depends on nothing about the
/// rows (see docs/design/test-isolation.md).
@SpringBootTest
class CatalogsTest
{
    @Autowired
    private QLiveDomain domain;

    @Autowired
    private QueryDocumentService queryDocumentService;

    @Autowired
    private GraphQL graphQL;

    @Autowired
    private DSLContext dslContext;


    @Test
    void holdsEveryRowOfACatalogTypeInPrimaryKeyOrder()
    {
        final List<Map<String, Object>> expected = dslContext.select(FOO_TYPE.ORDINAL, FOO_TYPE.NAME)
            .from(FOO_TYPE)
            .orderBy(FOO_TYPE.ORDINAL)
            .fetch(r -> Map.of("ordinal", r.value1(), "name", r.value2()));

        final Map<String, List<Map<String, Object>>> catalogs = Catalogs.load(domain, queryDocumentService, graphQL);

        assertThat(List.copyOf(catalogs.keySet()), contains("FooType"));
        assertThat(catalogs.get("FooType"), is(expected));
    }
}
