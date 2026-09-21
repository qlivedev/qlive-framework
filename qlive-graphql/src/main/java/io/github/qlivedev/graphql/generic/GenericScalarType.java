package io.github.qlivedev.graphql.generic;

import io.github.qlivedev.graphql.DomainQL;
import io.github.qlivedev.graphql.scalar.BigIntegerScalar;
import io.github.qlivedev.graphql.DomainQLAware;
import graphql.schema.GraphQLScalarType;

import java.math.BigInteger;
import java.util.Map;

public class GenericScalarType
{
    private GenericScalarType()
    {
        // no instances
    }

    private static final String NAME = "GenericScalar";

    public static GraphQLScalarType newGenericScalar()
    {
        return GraphQLScalarType.newScalar()
            .name(NAME)
            .description("Container for generic scalar values")
            .coercing(new GenericScalarCoercing())
            .build();
    }
}
