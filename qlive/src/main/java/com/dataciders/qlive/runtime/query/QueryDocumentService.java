package com.dataciders.qlive.runtime.query;

import com.dataciders.qlive.model.QueryConfig;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.constraints.NotNull;

public interface QueryDocumentService
{
    <T> QueryPlanBuilder<T> buildQuery(
        Class<T> type,
        DataFetchingEnvironment env,
        @NotNull QueryConfig config
    );
}
