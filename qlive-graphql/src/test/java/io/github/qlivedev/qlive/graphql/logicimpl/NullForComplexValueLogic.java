package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.GraphQLLogic;
import io.github.qlivedev.qlive.api.annotation.GraphQLQuery;
import io.github.qlivedev.qlive.graphql.beans.ComplexInput;

@GraphQLLogic
public class NullForComplexValueLogic
{
    @GraphQLQuery
    public boolean logicWithComplexInput(
        ComplexInput complexInput
    )
    {
        return complexInput == null;
    }
}
