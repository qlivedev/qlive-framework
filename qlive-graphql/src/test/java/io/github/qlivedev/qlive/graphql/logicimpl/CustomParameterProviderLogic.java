package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.GraphQLLogic;
import io.github.qlivedev.qlive.api.annotation.GraphQLQuery;
import io.github.qlivedev.qlive.graphql.beans.TestParamType;

@GraphQLLogic
public class CustomParameterProviderLogic
{

    @GraphQLQuery
    public String withCPP(TestParamType paramType)
    {
        return paramType.toString();
    }
}
