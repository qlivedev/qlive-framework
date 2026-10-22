package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.graphql.beans.ComplexInput;

@QLiveLogic
public class NullForComplexValueLogic
{
    @QLiveQuery
    public boolean logicWithComplexInput(
        ComplexInput complexInput
    )
    {
        return complexInput == null;
    }
}
