package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.logic.QLiveDataFetchingEnvironment;
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
