package io.github.qlivedev.qlive.graphql;

import io.github.qlivedev.qlive.graphql.beans.TestParamType;
import io.github.qlivedev.qlive.graphql.param.ParameterProvider;
import io.github.qlivedev.qlive.graphql.param.ParameterProviderFactory;

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
