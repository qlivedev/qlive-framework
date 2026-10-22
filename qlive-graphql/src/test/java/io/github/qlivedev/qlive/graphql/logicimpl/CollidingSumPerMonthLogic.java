package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.graphql.beans.collision.SumPerMonth;

@QLiveLogic
public class CollidingSumPerMonthLogic
{
    @QLiveQuery
    public SumPerMonth getCollidingSumPerMonth()
    {
        return new SumPerMonth();
    }
}
