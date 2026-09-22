package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.beans.collision.SumPerMonth;

@GraphQLLogic
public class CollidingSumPerMonthLogic
{
    @GraphQLQuery
    public SumPerMonth getCollidingSumPerMonth()
    {
        return new SumPerMonth();
    }
}
