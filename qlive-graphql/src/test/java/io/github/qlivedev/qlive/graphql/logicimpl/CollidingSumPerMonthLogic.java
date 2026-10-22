package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.GraphQLLogic;
import io.github.qlivedev.qlive.api.annotation.GraphQLQuery;
import io.github.qlivedev.qlive.graphql.beans.collision.SumPerMonth;

@GraphQLLogic
public class CollidingSumPerMonthLogic
{
    @GraphQLQuery
    public SumPerMonth getCollidingSumPerMonth()
    {
        return new SumPerMonth();
    }
}
