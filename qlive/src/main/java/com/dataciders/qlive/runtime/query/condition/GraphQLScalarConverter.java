package com.dataciders.qlive.runtime.query.condition;

import com.dataciders.qlive.runtime.QLiveException;
import de.quinscape.domainql.DomainQL;
import graphql.GraphQLContext;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLType;

import java.util.Locale;

/// Converts values with the coercing of the GraphQL scalar the FilterDSL node names, which is the same
/// route `ConditionCoercing` takes in the other direction.
public class GraphQLScalarConverter
    implements ScalarConverter
{
    private final DomainQL domainQL;

    private final GraphQLContext graphQLContext = GraphQLContext.newContext().build();


    public GraphQLScalarConverter(DomainQL domainQL)
    {
        this.domainQL = domainQL;
    }


    @Override
    public Object convert(String scalarType, Object value)
    {
        if (value == null)
        {
            return null;
        }

        if (scalarType == null)
        {
            throw new QLiveException("Filter value " + value + " has no scalar type");
        }

        final GraphQLType type = domainQL.getGraphQLSchema().getType(scalarType);
        if (!(type instanceof GraphQLScalarType scalar))
        {
            throw new QLiveException("Filter value type '" + scalarType + "' is not a scalar type: " + type);
        }

        return scalar.getCoercing().parseValue(value, graphQLContext, Locale.getDefault());
    }
}
