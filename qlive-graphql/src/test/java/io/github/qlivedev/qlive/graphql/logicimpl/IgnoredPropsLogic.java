package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.graphql.beans.IgnoredPropsBean;

@QLiveLogic
public class IgnoredPropsLogic
{
    @QLiveQuery
    public IgnoredPropsBean igPropBean()
    {
        return null;
    }
}
