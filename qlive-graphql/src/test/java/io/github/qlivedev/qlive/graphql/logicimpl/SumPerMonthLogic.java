package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.graphql.beans.SumPerMonth;

import jakarta.validation.constraints.NotNull;

@QLiveLogic
public class SumPerMonthLogic
{
    @QLiveQuery
    public SumPerMonth getSumPerMonthLogic(@NotNull int sum)
    {
        final SumPerMonth sumPerMonth = new SumPerMonth();
        sumPerMonth.setYear(2019);
        sumPerMonth.setMonth(6);
        sumPerMonth.setSum(sum);
        return sumPerMonth;
    }
}
