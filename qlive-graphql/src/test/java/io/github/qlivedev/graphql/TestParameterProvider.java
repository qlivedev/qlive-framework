package io.github.qlivedev.graphql;

import io.github.qlivedev.graphql.beans.TestParamType;
import io.github.qlivedev.graphql.param.ParameterProvider;
import graphql.schema.DataFetchingEnvironment;

public class TestParameterProvider
    implements ParameterProvider<TestParamType>
{
    @Override
    public TestParamType provide(DataFetchingEnvironment environment)
    {
        return new TestParamType();
    }
}
