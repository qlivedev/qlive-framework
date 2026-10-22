package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.graphql.beans.SourceOne;

@QLiveLogic
public class ImplicitOverrideNonInputLogic
{
    @QLiveQuery
    public boolean queryThatOverrides(SourceOne sourceOneInput)
    {
        return true;
    }
}
