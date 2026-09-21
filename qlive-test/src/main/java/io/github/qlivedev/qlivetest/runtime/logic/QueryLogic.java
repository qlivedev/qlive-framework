package io.github.qlivedev.qlivetest.runtime.logic;

import de.quinscape.domainql.annotation.GraphQLLogic;
import de.quinscape.domainql.annotation.GraphQLQuery;
import de.quinscape.domainql.annotation.GraphQLTypeParam;
import io.github.qlivedev.model.QueryConfig;
import io.github.qlivedev.model.QueryDocument;
import io.github.qlivedev.runtime.query.QueryDocumentService;
import io.github.qlivedev.qlivetest.domain.tables.pojos.AppUser;
import io.github.qlivedev.qlivetest.domain.tables.pojos.Bar;
import io.github.qlivedev.qlivetest.domain.tables.pojos.Baz;
import io.github.qlivedev.qlivetest.domain.tables.pojos.Foo;
import io.github.qlivedev.qlivetest.domain.tables.pojos.FooType;
import io.github.qlivedev.qlivetest.model.types.Qux;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;

/// Example logic
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


    /// Queries [T] objects based on the given query config
    @GraphQLQuery
    public <T> @NotNull QueryDocument<T> queryDocument(
        @GraphQLTypeParam(
            namePattern = "query*Document",
            typeNamePattern = "*Document",
            types = {
                Foo.class,
                FooType.class,
                Bar.class,
                Baz.class,
                AppUser.class,
                // the handwritten Qux, which is what puts it in the generated one's place
                Qux.class
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


//    @GraphQLQuery
//    public AllScalars queryAllScalars()
//    {
//        final FooType domainObject = new FooType();
//        domainObject.setName("Foo #1");
//        domainObject.setOrdinal(1);
//        return AllScalars.create(domainObject);
//    }
}
