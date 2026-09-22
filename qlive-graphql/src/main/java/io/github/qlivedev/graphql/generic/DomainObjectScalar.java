package io.github.qlivedev.graphql.generic;

import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.graphql.jooq.DomainObjectGeneratorStrategy;
import io.github.qlivedev.graphql.scalar.BigIntegerScalar;
import io.github.qlivedev.graphql.DomainQLAware;
import graphql.schema.GraphQLScalarType;

import java.math.BigInteger;
import java.util.Map;

/**
 * GraphQL Scalar implementation that wraps another domain type. The value is transmitted as a scalar but does the
 * appropriate conversions for the wrapped domain object.
 * <p>
 * This scalar allows to write GraphQL mutations that accept any of schema domain types. It requires that all domain
 * object
 * beans implement {@link DomainObject}. Configure the {@link DomainObjectGeneratorStrategy} for jooq to automatically
 * make the generated POJOs implement that interface.
 *
 * @see DomainObject
 */
public class DomainObjectScalar
{
    private DomainObjectScalar()
    {
        // no instances
    }


    public static GraphQLScalarType newDomainObjectScalar()
    {
        return GraphQLScalarType.newScalar()
            .name("DomainObject")
            .description("Container for generic domain objects as scalar")
            .coercing(new DomainObjectCoercing())
            .build();
    }
}
