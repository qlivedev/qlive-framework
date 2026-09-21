package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.beans.SourceSeven;
import io.github.qlivedev.graphql.beans.TargetSeven;

import java.util.UUID;

@GraphQLLogic 
public class OutputTypeOverrideLogic
{
    @GraphQLQuery
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

    @GraphQLQuery
    public SourceSeven sourceSevenOverload(SourceSeven in)
    {
        return null;
    }
}
