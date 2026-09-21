package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLMutation;
import io.github.qlivedev.graphql.beans.ConversionTarget;

@GraphQLLogic
public class TypeConversionLogic
{
    @GraphQLMutation
    public String mutateConverted(ConversionTarget target)
    {
        return target.getName() + ":" + target.getCreated();
    }
}
