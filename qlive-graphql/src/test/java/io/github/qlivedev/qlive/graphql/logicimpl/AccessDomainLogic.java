package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.GraphQLLogic;
import io.github.qlivedev.qlive.api.annotation.GraphQLQuery;
import io.github.qlivedev.qlive.graphql.logic.QLiveDataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironment;

@GraphQLLogic
public class AccessDomainLogic
{
    @GraphQLQuery
    public boolean accessDomainLogic(
        QLiveDataFetchingEnvironment environment
    )
    {
        return environment.getDomain() != null;
    }
}
