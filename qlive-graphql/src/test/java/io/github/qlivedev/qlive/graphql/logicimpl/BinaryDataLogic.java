package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.GraphQLLogic;
import io.github.qlivedev.qlive.api.annotation.GraphQLQuery;
import io.github.qlivedev.qlive.graphql.beans.BinaryBean;

@GraphQLLogic
public class BinaryDataLogic
{
    @GraphQLQuery
    public BinaryBean binaryBean()
    {
        final BinaryBean binaryBean = new BinaryBean();
        binaryBean.setName("Henry");
        binaryBean.setData("HELLO".getBytes());
        return binaryBean;
    }
}
