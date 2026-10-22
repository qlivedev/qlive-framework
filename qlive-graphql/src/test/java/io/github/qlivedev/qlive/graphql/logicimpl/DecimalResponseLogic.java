package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.graphql.beans.DecimalResponse;

@QLiveLogic
public class DecimalResponseLogic
{
    @QLiveQuery
    public DecimalResponse decimals()
    {
        return null;
    }
}
