package io.github.qlivedev.qlive.graphql;

import io.github.qlivedev.qlive.graphql.beans.TestParamType;
import io.github.qlivedev.qlive.graphql.param.ParameterProvider;
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
