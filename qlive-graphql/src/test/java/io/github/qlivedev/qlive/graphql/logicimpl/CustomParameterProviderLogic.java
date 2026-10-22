package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.graphql.beans.TestParamType;

@QLiveLogic
public class CustomParameterProviderLogic
{

    @QLiveQuery
    public String withCPP(TestParamType paramType)
    {
        return paramType.toString();
    }
}
