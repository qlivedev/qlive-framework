package io.github.qlivedev.runtime.query;

import io.github.qlivedev.model.QueryConfig;
import graphql.schema.DataFetchingEnvironment;

/// Executes the generic [T] document queries the application's query logic exposes over GraphQL.
///
/// The interface a logic bean sees. {@link DefaultQueryDocumentService} is the implementation querying the
/// application's JOOQ schema; an application needing something else registers its own bean.
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
