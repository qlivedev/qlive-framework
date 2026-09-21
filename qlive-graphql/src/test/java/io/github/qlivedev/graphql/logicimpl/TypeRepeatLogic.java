package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.beans.BeanWithSix;

@GraphQLLogic
public class TypeRepeatLogic
{
    @GraphQLQuery
    public BeanWithSix beanWithSix()
    {
        return null;
    }
}
