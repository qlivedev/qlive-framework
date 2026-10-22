package io.github.qlivedev.qlive.graphql.generic;

import io.github.qlivedev.qlive.api.generic.GenericScalar;
import io.github.qlivedev.qlive.graphql.QLiveDomain;
import io.github.qlivedev.qlive.graphql.scalar.BigIntegerScalar;
import io.github.qlivedev.qlive.graphql.QLiveDomainAware;
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
