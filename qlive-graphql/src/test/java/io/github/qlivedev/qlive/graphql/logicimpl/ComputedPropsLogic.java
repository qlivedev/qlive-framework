package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.GraphQLLogic;
import io.github.qlivedev.qlive.api.annotation.GraphQLQuery;
import io.github.qlivedev.qlive.graphql.beans.ComputedPropsBean;

@GraphQLLogic
public class ComputedPropsLogic
{
    @GraphQLQuery
    public ComputedPropsBean computedPropsBean(ComputedPropsBean in)
    {
        return in;
    }
}
