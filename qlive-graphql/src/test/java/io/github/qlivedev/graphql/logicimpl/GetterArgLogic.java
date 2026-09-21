package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.beans.GetterArgBean;

@GraphQLLogic
public class GetterArgLogic
{
    @GraphQLQuery
    public GetterArgBean getterArgBean()
    {
        return new GetterArgBean("Value From GetterArgLogic");
    }
}
