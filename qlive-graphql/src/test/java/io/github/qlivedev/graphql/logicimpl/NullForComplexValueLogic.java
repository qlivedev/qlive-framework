package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.beans.ComplexInput;

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
