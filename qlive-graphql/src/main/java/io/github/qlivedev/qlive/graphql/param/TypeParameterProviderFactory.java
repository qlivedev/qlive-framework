package io.github.qlivedev.qlive.graphql.param;

import io.github.qlivedev.qlive.api.annotation.QLiveTypeParam;

import java.lang.annotation.Annotation;

/**
 * Factory for the DataFetchingEnvironmentProvider.
 */
public class TypeParameterProviderFactory
    implements ParameterProviderFactory
{
    @Override
    public ParameterProvider createIfApplicable(
        Class<?> parameterClass, Annotation[] annotations
    )
    {
        for (int i = 0; i < annotations.length; i++)
        {
            Annotation annotation = annotations[i];
            if (annotation instanceof QLiveTypeParam)
            {
                return TypeParameterProvider.INSTANCE;
            }
        }
        return null;
    }
}
