package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.graphql.beans.ComputedPropsBean;

@QLiveLogic
public class ComputedPropsLogic
{
    @QLiveQuery
    public ComputedPropsBean computedPropsBean(ComputedPropsBean in)
    {
        return in;
    }
}
