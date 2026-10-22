package io.github.qlivedev.qlive.graphql.param;

import graphql.schema.DataFetchingEnvironment;

/**
 * Implemented by classes providing values for a @{@link io.github.qlivedev.qlive.api.annotation.GraphQLLogic} method parameters.
 *
 * @param <T> parameter type provided by this parameter provider
 */
public interface ParameterProvider<T>
{
    /**
     * Provides a value for the given data fetching environment.
     *
     * @param environment   environment
     *
     * @return parameter value
     */
    T provide(DataFetchingEnvironment environment);
}
