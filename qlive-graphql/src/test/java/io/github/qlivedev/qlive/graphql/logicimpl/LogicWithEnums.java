package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.graphql.beans.BeanWithEnum;
import io.github.qlivedev.qlive.graphql.beans.MyEnum;

@QLiveLogic
public class LogicWithEnums
{
    @QLiveQuery
    public String queryWithEnumArg(MyEnum myEnum)
    {
        return "(" + myEnum.name() + ")";
    }

    @QLiveQuery
    public String queryWithObjectArgWithEnum(BeanWithEnum beanWithEnum)
    {
        return beanWithEnum.toString();
    }

}
