package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.beans.BeanWithFetcher;

@GraphQLLogic
public class CustomFetcherLogic
{
    @GraphQLQuery
    public BeanWithFetcher beanWithFetcher()
    {
        final BeanWithFetcher bean = new BeanWithFetcher();
        bean.setValue("Value From Logic");
        return bean;
    }
}
