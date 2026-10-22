package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.graphql.beans.NoMirrorBean;

@QLiveLogic
public class NoMirrorLogic
{
    @QLiveQuery
    public NoMirrorBean getValue()
    {
        return null;
    }
}
