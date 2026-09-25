package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.logic.QLiveDataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironment;

@GraphQLLogic
public class AccessDomainQLLogic
{
    @GraphQLQuery
    public boolean accessDomainQLLogic(
        QLiveDataFetchingEnvironment environment
    )
    {
        return environment.getDomain() != null;
    }
}
