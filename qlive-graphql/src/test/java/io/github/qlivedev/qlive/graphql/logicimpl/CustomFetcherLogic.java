package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.GraphQLLogic;
import io.github.qlivedev.qlive.api.annotation.GraphQLQuery;
import io.github.qlivedev.qlive.graphql.beans.BeanWithFetcher;

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
