package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.GraphQLLogic;
import io.github.qlivedev.qlive.api.annotation.GraphQLMutation;
import io.github.qlivedev.qlive.graphql.beans.ConversionTarget;

@GraphQLLogic
public class TypeConversionLogic
{
    @GraphQLMutation
    public String mutateConverted(ConversionTarget target)
    {
        return target.getName() + ":" + target.getCreated();
    }
}
