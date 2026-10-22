package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.GraphQLLogic;
import io.github.qlivedev.qlive.api.annotation.GraphQLQuery;
import io.github.qlivedev.qlive.graphql.beans.GetterArgBean;

@GraphQLLogic
public class GetterArgLogic
{
    @GraphQLQuery
    public GetterArgBean getterArgBean()
    {
        return new GetterArgBean("Value From GetterArgLogic");
    }
}
