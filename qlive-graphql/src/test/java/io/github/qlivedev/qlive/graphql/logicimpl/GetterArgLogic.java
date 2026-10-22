package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.graphql.beans.GetterArgBean;

@QLiveLogic
public class GetterArgLogic
{
    @QLiveQuery
    public GetterArgBean getterArgBean()
    {
        return new GetterArgBean("Value From GetterArgLogic");
    }
}
