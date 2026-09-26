package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.beans.ComputedPropsBean;

@GraphQLLogic
public class ComputedPropsLogic
{
    @GraphQLQuery
    public ComputedPropsBean computedPropsBean(ComputedPropsBean in)
    {
        return in;
    }
}
