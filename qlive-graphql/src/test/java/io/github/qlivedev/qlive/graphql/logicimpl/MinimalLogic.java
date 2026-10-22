package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.GraphQLLogic;
import io.github.qlivedev.qlive.api.annotation.GraphQLMutation;
import io.github.qlivedev.qlive.api.annotation.GraphQLQuery;

@GraphQLLogic
public class MinimalLogic
{
    @GraphQLQuery
    public boolean query()
    {
        return true;
    }

    @GraphQLMutation
    public boolean mutation()
    {
        return false;
    }
}
