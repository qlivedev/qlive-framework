package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.beans.TestParamType;

@GraphQLLogic
public class CustomParameterProviderLogic
{

    @GraphQLQuery
    public String withCPP(TestParamType paramType)
    {
        return paramType.toString();
    }
}
