package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.graphql.beans.BeanWithFetcher;

@QLiveLogic
public class CustomFetcherLogic
{
    @QLiveQuery
    public BeanWithFetcher beanWithFetcher()
    {
        final BeanWithFetcher bean = new BeanWithFetcher();
        bean.setValue("Value From Logic");
        return bean;
    }
}
