package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.beans.BinaryBean;

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
