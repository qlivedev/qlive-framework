package io.github.qlivedev.qlivetest.runtime.logic;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.api.annotation.QLiveTypeParam;
import io.github.qlivedev.qlive.model.QueryConfig;
import io.github.qlivedev.qlive.model.QueryDocument;
import io.github.qlivedev.qlive.runtime.query.QueryDocumentService;
import io.github.qlivedev.qlivetest.domain.tables.pojos.AppUser;
import io.github.qlivedev.qlivetest.domain.tables.pojos.Bar;
import io.github.qlivedev.qlivetest.domain.tables.pojos.Baz;
import io.github.qlivedev.qlivetest.domain.tables.pojos.Foo;
import io.github.qlivedev.qlivetest.domain.tables.pojos.FooType;
import io.github.qlivedev.qlivetest.domain.tables.pojos.Quux;
import io.github.qlivedev.qlivetest.model.types.Qux;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;

/// Example logic
@QLiveLogic
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


    /// Queries [T] objects based on the given query config
    @QLiveQuery
    public <T> @NotNull QueryDocument<T> queryDocument(
        @QLiveTypeParam(
            namePattern = "query*Document",
            typeNamePattern = "*Document",
            types = {
                Foo.class,
                FooType.class,
                Bar.class,
                Baz.class,
                AppUser.class,
                // the handwritten Qux, which is what puts it in the generated one's place
                Qux.class,
                Quux.class
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


//    @QLiveQuery
//    public AllScalars queryAllScalars()
//    {
//        final FooType domainObject = new FooType();
//        domainObject.setName("Foo #1");
//        domainObject.setOrdinal(1);
//        return AllScalars.create(domainObject);
//    }
}
