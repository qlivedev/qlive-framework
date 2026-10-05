package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.beans.SizedResponse;

@GraphQLLogic
public class SizedResponseLogic
{
    @GraphQLQuery
    public SizedResponse sized()
    {
        return null;
    }
}
