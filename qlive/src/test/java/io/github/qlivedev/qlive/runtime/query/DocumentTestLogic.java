package io.github.qlivedev.qlive.runtime.query;

import io.github.qlivedev.qlive.api.annotation.GraphQLLogic;
import io.github.qlivedev.qlive.api.annotation.GraphQLQuery;
import io.github.qlivedev.qlive.api.annotation.GraphQLTypeParam;
import io.github.qlivedev.qlive.model.QueryConfig;
import io.github.qlivedev.qlive.model.QueryDocument;
import io.github.qlivedev.qlive.testdomain.tables.pojos.TestBar;
import io.github.qlivedev.qlive.testdomain.tables.pojos.TestBaz;
import io.github.qlivedev.qlive.testdomain.tables.pojos.TestUser;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.constraints.NotNull;

/// Logic bean that answers query document queries the way an application's does: by handing them to the
/// query document service, which plans and executes them.
///
/// The service needs the schema this bean is part of, so it is set after the domain is built rather than
/// passed in, which is what an application's `@Lazy` injection does for it.
@GraphQLLogic
public class DocumentTestLogic
{
    private QueryDocumentService queryDocumentService;


    public void setQueryDocumentService(QueryDocumentService queryDocumentService)
    {
        this.queryDocumentService = queryDocumentService;
    }


    @GraphQLQuery
    public <T> @NotNull QueryDocument<T> queryDocument(
        @GraphQLTypeParam(
            namePattern = "query*Document",
            typeNamePattern = "*Document",
            types = {
                io.github.qlivedev.qlive.testmodel.types.TestFoo.class,
                TestUser.class,
                TestBar.class,
                TestBaz.class
            }
        )
        Class<T> type,
        DataFetchingEnvironment env,
        @NotNull QueryConfig config
    )
    {
        return queryDocumentService.buildQuery(type, env, config)
            .selectByFilter(true)
            .execute();
    }
}
