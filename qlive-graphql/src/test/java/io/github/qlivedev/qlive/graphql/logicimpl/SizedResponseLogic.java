package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.graphql.beans.SizedResponse;

@QLiveLogic
public class SizedResponseLogic
{
    @QLiveQuery
    public SizedResponse sized()
    {
        return null;
    }
}
