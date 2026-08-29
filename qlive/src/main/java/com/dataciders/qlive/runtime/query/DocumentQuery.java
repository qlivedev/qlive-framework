package com.dataciders.qlive.runtime.query;

import com.dataciders.qlive.model.QueryDocument;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/// Encapsulates a complete query within the [QueryDocumentService]. Contains one or more query executions which
/// are connected into one big graph of domain objects and fetcher contexts ^*
///
/// ^* Note that the majority of domain objects are usually JOOQ-generated. This means that the POJOs themselves are
/// flat and the relations expressed in the database as foreign keys don't exist as typed fields in the POJOs but only
/// as fields in GraphQL. For query execution, this means that these fields are provided in the fetcher context.
///
/// @param <T>
public class DocumentQuery<T>
{
    private final QueryExecutionContext ctx;
    private final List<QueryExecution> queryExecutions;

    public DocumentQuery(QueryExecutionContext ctx)
    {
        this.ctx = ctx;
        queryExecutions = new ArrayList<>();
    }


    public QueryExecutionContext getCtx()
    {
        return ctx;
    }


    /// Adds the given execution to the list of executions
    public void addExecution(QueryExecution queryExecution)
    {
        queryExecutions.add(queryExecution);
    }

    
    /// Returns the list of executions
    public List<QueryExecution> getQueryExecutions()
    {
        return queryExecutions;
    }


    public @NotNull QueryDocument<T> execute()
    {
        throw new UnsupportedOperationException("Query execution is not yet implemented");
    }


    @Override
    public String toString()
    {
        return super.toString() + ": "
            + "queryExecutions = " + queryExecutions.stream().map(QueryExecution::toString).collect(
            Collectors.joining("\n"))
            ;
    }
}
