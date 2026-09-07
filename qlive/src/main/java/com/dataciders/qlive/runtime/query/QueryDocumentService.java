package com.dataciders.qlive.runtime.query;

import com.dataciders.qlive.model.QueryConfig;
import graphql.schema.DataFetchingEnvironment;

/// Executes the generic [T] document queries the application's query logic exposes over GraphQL.
///
/// Placeholder for the rebuilt service: only the API the framework user's logic beans already call
/// exists so far, none of the query execution behind it.
public interface QueryDocumentService
{
    /// Starts building the query for a single query document.
    ///
    /// @param type     runtime payload type of the document
    /// @param env      data fetching environment of the GraphQL query asking for the document
    /// @param config   query config given by the client
    ///
    /// @return query to configure further and to execute
    <T> DocumentQueryBuilder<T> buildQuery(Class<T> type, DataFetchingEnvironment env, QueryConfig config);
}
