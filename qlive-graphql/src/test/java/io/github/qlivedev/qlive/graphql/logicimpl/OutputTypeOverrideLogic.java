package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.graphql.beans.SourceSeven;
import io.github.qlivedev.qlive.graphql.beans.TargetSeven;

import java.util.UUID;

@QLiveLogic 
public class OutputTypeOverrideLogic
{
    @QLiveQuery
    public TargetSeven targetSeven(TargetSeven targetSeven)
    {
        if (targetSeven.getId() == null)
        {
            targetSeven.setId(UUID.randomUUID().toString());
        }
        if (targetSeven.getName() == null)
        {
            targetSeven.setName("Unnamed");
        }
        return targetSeven;
    }

    @QLiveQuery
    public SourceSeven sourceSevenOverload(SourceSeven in)
    {
        return null;
    }
}
