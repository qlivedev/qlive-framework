package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.annotation.GraphQLTypeParam;
import io.github.qlivedev.graphql.beans.Container;
import io.github.qlivedev.graphql.beans.TargetSeven;
import graphql.schema.DataFetchingEnvironment;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@GraphQLLogic 
public class OutputTypeOverrideByParamLogic
{

    @GraphQLQuery
    public <T> Container<T> parametrized(
        @GraphQLTypeParam(
            types = {
                // XXX: overriding type here
                TargetSeven.class
            }
        )
            Class<T> type,
        DataFetchingEnvironment env
    )
    {
        return null;

    }
}
