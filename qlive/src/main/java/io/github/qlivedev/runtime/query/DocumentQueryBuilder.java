package io.github.qlivedev.runtime.query;

import io.github.qlivedev.model.QueryConfig;
import io.github.qlivedev.model.QueryDocument;
import graphql.schema.DataFetchingEnvironment;

import java.util.List;

/// One query document query in the making: what the client asked for, plus the options the logic bean adds
/// before executing it.
public class DocumentQueryBuilder<T>
{
    /// How the query gets run once it is configured. Keeps the builder from being tied to one
    /// implementation of {@link QueryDocumentService}.
    @FunctionalInterface
    public interface Execution<T>
    {
        QueryDocument<T> execute(DocumentQueryBuilder<T> query);
    }


    private final Execution<T> execution;

    private final Class<T> type;

    private final DataFetchingEnvironment env;

    private final List<String> fields;

    private final QueryConfig config;

    private boolean selectByFilter;


    public DocumentQueryBuilder(
        Execution<T> execution,
        Class<T> type,
        DataFetchingEnvironment env,
        QueryConfig config
    )
    {
        this(execution, type, env, null, config);
    }


    public DocumentQueryBuilder(
        Execution<T> execution,
        Class<T> type,
        List<String> fields,
        QueryConfig config
    )
    {
        this(execution, type, null, List.copyOf(fields), config);
    }


    private DocumentQueryBuilder(
        Execution<T> execution,
        Class<T> type,
        DataFetchingEnvironment env,
        List<String> fields,
        QueryConfig config
    )
    {
        this.execution = execution;
        this.type = type;
        this.env = env;
        this.fields = fields;
        this.config = config == null ? new QueryConfig() : config;
    }


    public Class<T> getType()
    {
        return type;
    }


    /// The environment of the GraphQL query selecting the document, `null` for a query of {@link #getFields()}.
    public DataFetchingEnvironment getEnv()
    {
        return env;
    }


    /// The fields of the row type a query outside GraphQL selects, `null` for a GraphQL query.
    public List<String> getFields()
    {
        return fields;
    }


    public QueryConfig getConfig()
    {
        return config;
    }


    public boolean isSelectByFilter()
    {
        return selectByFilter;
    }


    /// Setting this to `true` allows the fields implicitly being selected because they are referenced in a
    /// FilterDSL field.
    public DocumentQueryBuilder<T> selectByFilter(boolean selectByFilter)
    {
        this.selectByFilter = selectByFilter;
        return this;
    }


    /// Executes the query and returns the resulting document.
    public QueryDocument<T> execute()
    {
        return execution.execute(this);
    }
}
