package com.dataciders.qlivetest.runtime.logic;

import de.quinscape.domainql.annotation.GraphQLLogic;
import de.quinscape.domainql.annotation.GraphQLQuery;
import de.quinscape.domainql.annotation.GraphQLTypeParam;
import com.dataciders.qlive.model.QueryConfig;
import com.dataciders.qlive.model.QueryDocument;
import com.dataciders.qlive.runtime.query.QueryDocumentService;
import com.dataciders.qlivetest.domain.tables.pojos.AppUser;
import com.dataciders.qlivetest.domain.tables.pojos.Bar;
import com.dataciders.qlivetest.domain.tables.pojos.Baz;
import com.dataciders.qlivetest.domain.tables.pojos.Foo;
import com.dataciders.qlivetest.domain.tables.pojos.FooType;
import de.quinscape.domainql.fetcher.FetcherContext;
import de.quinscape.domainql.jooq.GeneratedDomainObject;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;

import java.lang.reflect.InvocationTargetException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

@GraphQLLogic
public class QueryLogic
{
    private final static Logger log = LoggerFactory.getLogger(QueryLogic.class);

    private final QueryDocumentService queryDocumentService;


    public QueryLogic(
        @Lazy QueryDocumentService queryDocumentService
    )
    {
        this.queryDocumentService = queryDocumentService;
    }


    /**
     * Queries [T] objects based on the given query config
     */
    @GraphQLQuery
    public <T> @NotNull QueryDocument<T> queryDocument(
        @GraphQLTypeParam(
            namePattern = "query*Document",
            typeNamePattern = "*Document",
            types = {
                FooType.class,
                Bar.class,
                Baz.class,
                AppUser.class
            }
        )
        Class<T> type,
        DataFetchingEnvironment env,
        @NotNull QueryConfig config
    )
    {

        log.debug("QueryDocument<{}>, config = {}", type, config);

        return queryDocumentService.buildQuery(type, env, config)
            .selectByFilter(true)
            .execute();
    }


    // Test method querying [T]
    @GraphQLQuery
    public <T> @NotNull QueryDocument<T> testQueryDocument(
        @GraphQLTypeParam(
            namePattern = "query*Document",
            typeNamePattern = "*Document",
            types = {
                Foo.class
            }
        )
        Class<T> type,
        DataFetchingEnvironment env,
        @NotNull QueryConfig config
    )
    {
        final QueryDocument<Foo> doc = new QueryDocument<>(Foo.class);
        if (config == null)
        {
            config = new QueryConfig();
            config.setPageSize(1);

        }
        doc.setConfig(config);
        final ArrayList<Foo> rows = new ArrayList<>();
        final Foo foo = new Foo();

        int rnd = (int) Math.round(Math.random() * 100);

        foo.setId(UUID.randomUUID().toString());
        foo.setName("Foo #" + rnd);
        foo.setNum(rnd);
        foo.setDescription("Desc for Foo #" + rnd);
        foo.setOwnerId("d7df0f2c-9aa8-4845-b2bf-1d02abd3666e");
        final FetcherContext fetcherContext = new FetcherContext();
        final AppUser user = new AppUser();
        user.setId("d7df0f2c-9aa8-4845-b2bf-1d02abd3666e");
        user.setLogin("admin");
        user.setCreated(Timestamp.from(Instant.now()));
        fetcherContext.setProperty("owner", user);
        foo.provideFetcherContext(fetcherContext);

        rows.add(foo);

        doc.setRows(rows);
        doc.setRowCount(1);

        return (QueryDocument<T>) doc;
    }

//    @GraphQLQuery
//    public AllScalars queryAllScalars()
//    {
//        final FooType domainObject = new FooType();
//        domainObject.setName("Foo #1");
//        domainObject.setOrdinal(1);
//        return AllScalars.create(domainObject);
//    }
}
