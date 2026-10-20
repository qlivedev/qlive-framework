package io.github.qlivedev.runtime.query;

import io.github.qlivedev.model.QueryConfig;
import graphql.schema.DataFetchingEnvironment;

import java.util.List;

/// Executes the generic document queries the application's query logic exposes over GraphQL.
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
    /// @return query result with embedded configuration for re-execution
    <T> DocumentQueryBuilder<T> buildQuery(Class<T> type, DataFetchingEnvironment env, QueryConfig config);


    /// Starts building a query of a type's rows outside GraphQL: the given fields of each row, and no relation.
    ///
    /// @param type     runtime payload type of the rows
    /// @param fields   names of the fields to select, each a column of the type
    /// @param config   query config given by the client
    ///
    /// @return query result with embedded configuration for re-execution
    <T> DocumentQueryBuilder<T> buildQuery(Class<T> type, List<String> fields, QueryConfig config);
}
