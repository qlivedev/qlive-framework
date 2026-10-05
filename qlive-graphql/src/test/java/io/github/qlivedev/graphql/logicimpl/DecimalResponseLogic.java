package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.beans.DecimalResponse;

@GraphQLLogic
public class DecimalResponseLogic
{
    @GraphQLQuery
    public DecimalResponse decimals()
    {
        return null;
    }
}
