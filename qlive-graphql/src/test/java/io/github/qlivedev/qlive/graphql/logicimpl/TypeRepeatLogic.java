package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.GraphQLLogic;
import io.github.qlivedev.qlive.api.annotation.GraphQLQuery;
import io.github.qlivedev.qlive.graphql.beans.BeanWithSix;

@GraphQLLogic
public class TypeRepeatLogic
{
    @GraphQLQuery
    public BeanWithSix beanWithSix()
    {
        return null;
    }
}
