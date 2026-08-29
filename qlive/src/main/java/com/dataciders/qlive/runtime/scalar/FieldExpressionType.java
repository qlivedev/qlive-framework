package com.dataciders.qlive.runtime.scalar;

import graphql.schema.GraphQLScalarType;

/**
 * Scalar type that encapsulates JOOQ node expression as object graph converting scalar values where necessary.
 */
public class FieldExpressionType
{
    private static final String NAME = "FieldExpression";

    private FieldExpressionType()
    {
        // no instances
    }

    public static GraphQLScalarType newFieldExpressionType()
    {
        return GraphQLScalarType.newScalar()
            .name(NAME)
            .description("Map graph representing a JOOQ node expression")
            .coercing(new FieldExpressionCoercing())
            .build();
    }
}
