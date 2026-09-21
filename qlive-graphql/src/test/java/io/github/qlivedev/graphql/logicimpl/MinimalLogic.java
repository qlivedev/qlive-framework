package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLMutation;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;

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
