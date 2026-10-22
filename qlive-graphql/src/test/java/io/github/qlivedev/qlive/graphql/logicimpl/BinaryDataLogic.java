package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.graphql.beans.BinaryBean;

@QLiveLogic
public class BinaryDataLogic
{
    @QLiveQuery
    public BinaryBean binaryBean()
    {
        final BinaryBean binaryBean = new BinaryBean();
        binaryBean.setName("Henry");
        binaryBean.setData("HELLO".getBytes());
        return binaryBean;
    }
}
