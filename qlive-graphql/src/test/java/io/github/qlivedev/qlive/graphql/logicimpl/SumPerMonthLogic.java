package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.GraphQLLogic;
import io.github.qlivedev.qlive.api.annotation.GraphQLQuery;
import io.github.qlivedev.qlive.graphql.beans.SumPerMonth;

import jakarta.validation.constraints.NotNull;

@GraphQLLogic
public class SumPerMonthLogic
{
    @GraphQLQuery
    public SumPerMonth getSumPerMonthLogic(@NotNull int sum)
    {
        final SumPerMonth sumPerMonth = new SumPerMonth();
        sumPerMonth.setYear(2019);
        sumPerMonth.setMonth(6);
        sumPerMonth.setSum(sum);
        return sumPerMonth;
    }
}
