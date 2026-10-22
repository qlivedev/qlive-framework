package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.GraphQLLogic;
import io.github.qlivedev.qlive.api.annotation.GraphQLQuery;
import io.github.qlivedev.qlive.api.annotation.GraphQLTypeParam;
import io.github.qlivedev.qlive.graphql.beans.Container;
import io.github.qlivedev.qlive.graphql.beans.TargetSeven;
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
