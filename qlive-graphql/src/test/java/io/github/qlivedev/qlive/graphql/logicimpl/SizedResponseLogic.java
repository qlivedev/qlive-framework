package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.GraphQLLogic;
import io.github.qlivedev.qlive.api.annotation.GraphQLQuery;
import io.github.qlivedev.qlive.graphql.beans.SizedResponse;

@GraphQLLogic
public class SizedResponseLogic
{
    @GraphQLQuery
    public SizedResponse sized()
    {
        return null;
    }
}
