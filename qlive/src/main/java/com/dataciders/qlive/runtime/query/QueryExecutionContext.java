package com.dataciders.qlive.runtime.query;

import de.quinscape.domainql.DomainQL;
import de.quinscape.domainql.fetcher.ReferenceFetcher;
import com.dataciders.qlive.model.QueryConfig;
import com.dataciders.qlive.runtime.util.Util;
import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLUnmodifiedType;
import org.jooq.DSLContext;

import java.beans.Introspector;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class QueryExecutionContext
{
    private final DomainQL domainQL;

    private final DSLContext dslContext;

    private final GraphQL graphQL;

    private final Class<?> type;

    private final DataFetchingEnvironment env;

    private final QueryConfig config;


    /// Maps a type name to its short name base. Uses initials created by {@link Util#getInitials(String)} if they
    /// are unique within the query, otherwise we use the decapitalized type name.
    private final Map<String, String> shortNameBase;


    public <T> QueryExecutionContext(
        DomainQL domainQL,
        DSLContext dslContext,
        GraphQL graphQL,
        Class<T> type,
        DataFetchingEnvironment env,
        QueryConfig config
    )
    {
        this.domainQL = domainQL;
        this.dslContext = dslContext;
        this.graphQL = graphQL;
        this.type = type;
        this.env = env;
        this.config = config;

        shortNameBase = determineNamingRules(env);
    }

    /// Inspects all table types and relations within the query and returns the names of the types and relation field names
    /// mapped to their short name base. Types / relations fields with unique initials use initials, otherwise the
    /// decapitalized name is used
    private Map<String, String> determineNamingRules(DataFetchingEnvironment env)
    {
        final Map<String, Set<String>> shortNamesInQuery = createNamesPerInitialMap(env);

        final Map<String, String> shortNameBase = new HashMap<>();
        for (Set<String> types : shortNamesInQuery.values())
        {
            if (types.size() == 1)
            {
                final String typeName = types.iterator().next();
                shortNameBase.put(typeName, Util.getInitials(typeName));
            }
            else
            {
                for (String typeName : types)
                {
                    shortNameBase.put(typeName, Introspector.decapitalize(typeName));
                }
            }
        }
        return shortNameBase;
    }


    private Map<String, Set<String>> createNamesPerInitialMap(DataFetchingEnvironment env)
    {
        final Map<String, Set<String>> shortNamesToTypeList = new HashMap<>();

        env.getSelectionSet().getFields()
            .stream()
            .filter(selectedField -> {
                final GraphQLUnmodifiedType type = GraphQLTypeUtil.unwrapAll(selectedField.getType());
                return !GraphQLTypeUtil.isScalar(type) && !Util.isQueryDocumentType(domainQL, type.getName());
            })
            .forEach(complexField -> {

                final GraphQLObjectType objectType = complexField.getObjectTypes().get(0);

                final DataFetcher<?> dataFetcher = domainQL.getGraphQLSchema().getCodeRegistry().getDataFetcher(objectType, objectType.getFieldDefinition(complexField.getName()));

                String shortName;
                String source;
                if (dataFetcher instanceof ReferenceFetcher)
                {
                    source = complexField.getName();
                    shortName = Util.getInitials(source);
                }
                else
                {
                    final GraphQLObjectType type = (GraphQLObjectType) GraphQLTypeUtil.unwrapAll(complexField.getType());
                    source = type.getName();
                    shortName = Util.getInitials(source);
                }

                Set<String> types = shortNamesToTypeList.computeIfAbsent(shortName, k -> new HashSet<>());
                types.add(source);
            });
        return shortNamesToTypeList;
    }


    public DomainQL getDomainQL()
    {
        return domainQL;
    }


    public DSLContext getDslContext()
    {
        return dslContext;
    }


    public GraphQL getGraphQL()
    {
        return graphQL;
    }


    public Class<?> getType()
    {
        return type;
    }


    public DataFetchingEnvironment getEnv()
    {
        return env;
    }


    public QueryConfig getConfig()
    {
        return config;
    }


    /// Creates a short name based on the names of all complex types in the query.
    ///
    /// If the initials of the type are unique within the query, it will generate a unique name based on initial,
    /// otherwise it will decapitalize the type name and use that as base for the unique name creation
    ///
    /// @param typeName     GraphQL object type name
    ///
    /// @see #determineNamingRules(DataFetchingEnvironment)
    ///
    /// @return unique type alias for given type
    public String getShortName(String typeName)
    {
        final String base = shortNameBase.get(typeName);
        if (base == null)
        {
            throw new IllegalStateException("No short name prepared for type: " + typeName);
        }
        return base;
    }

    public String getShortName(QueryExecution queryExecution, String typeName)
    {
        return queryExecution.getUniqueName(getShortName(typeName));
    }



    @Override
    public String toString()
    {
        return "QueryExecutionContext[" +
            "domainQL=" + domainQL + ", " +
            "dslContext=" + dslContext + ", " +
            "graphQL=" + graphQL + ", " +
            "env=" + env + ']';
    }
}
