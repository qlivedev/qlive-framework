package io.github.qlivedev.graphql;

import io.github.qlivedev.graphql.beans.TestParamType;
import io.github.qlivedev.graphql.param.ParameterProvider;
import io.github.qlivedev.graphql.param.ParameterProviderFactory;

import java.lang.annotation.Annotation;

public class TestParameterProviderFactory
    implements ParameterProviderFactory
{
    @Override
    public ParameterProvider createIfApplicable(
        Class<?> parameterClass, Annotation[] annotations
    ) throws Exception
    {
        if (parameterClass.equals(TestParamType.class))
        {
            return new TestParameterProvider();
        }
        return null;
    }
}
