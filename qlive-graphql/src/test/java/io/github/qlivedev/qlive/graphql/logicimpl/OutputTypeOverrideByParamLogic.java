package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.api.annotation.QLiveTypeParam;
import io.github.qlivedev.qlive.graphql.beans.Container;
import io.github.qlivedev.qlive.graphql.beans.TargetSeven;
import graphql.schema.DataFetchingEnvironment;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@QLiveLogic 
public class OutputTypeOverrideByParamLogic
{

    @QLiveQuery
    public <T> Container<T> parametrized(
        @QLiveTypeParam(
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
