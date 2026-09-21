package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.beans.SumPerMonth;

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
