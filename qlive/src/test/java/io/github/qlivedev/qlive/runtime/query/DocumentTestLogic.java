package io.github.qlivedev.qlive.runtime.query;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.api.annotation.QLiveTypeParam;
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
@QLiveLogic
public class DocumentTestLogic
{
    private QueryDocumentService queryDocumentService;


    public void setQueryDocumentService(QueryDocumentService queryDocumentService)
    {
        this.queryDocumentService = queryDocumentService;
    }


    @QLiveQuery
    public <T> @NotNull QueryDocument<T> queryDocument(
        @QLiveTypeParam(
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
