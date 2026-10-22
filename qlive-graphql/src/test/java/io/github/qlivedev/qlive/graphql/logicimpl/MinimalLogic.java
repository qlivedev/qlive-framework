package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveMutation;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;

@QLiveLogic
public class MinimalLogic
{
    @QLiveQuery
    public boolean query()
    {
        return true;
    }

    @QLiveMutation
    public boolean mutation()
    {
        return false;
    }
}
