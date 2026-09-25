package io.github.qlivedev.graphql.param;

import io.github.qlivedev.graphql.logic.QLiveDataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironment;

/**
 * Provides the current type parameter value for {@link io.github.qlivedev.graphql.annotation.GraphQLTypeParam} annotated
 * parameters.
 */
public class TypeParameterProvider
    implements ParameterProvider<Class<?>>
{
    public final static TypeParameterProvider INSTANCE = new TypeParameterProvider();

    private TypeParameterProvider()
    {
    }


    @Override
    public Class<?> provide(DataFetchingEnvironment environment)
    {
        if (!(environment instanceof QLiveDataFetchingEnvironment))
        {
            throw new IllegalStateException("Provided environment is not an instance of " + QLiveDataFetchingEnvironment.class);
        }

        return ((QLiveDataFetchingEnvironment)environment).getTypeParam();
    }
}
