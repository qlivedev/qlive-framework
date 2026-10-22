package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.graphql.logic.QLiveDataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironment;

@QLiveLogic
public class AccessDomainLogic
{
    @QLiveQuery
    public boolean accessDomainLogic(
        QLiveDataFetchingEnvironment environment
    )
    {
        return environment.getDomain() != null;
    }
}
