package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.GraphQLLogic;
import io.github.qlivedev.qlive.api.annotation.GraphQLQuery;
import io.github.qlivedev.qlive.graphql.beans.BeanWithEnum;
import io.github.qlivedev.qlive.graphql.beans.MyEnum;

@GraphQLLogic
public class LogicWithEnums
{
    @GraphQLQuery
    public String queryWithEnumArg(MyEnum myEnum)
    {
        return "(" + myEnum.name() + ")";
    }

    @GraphQLQuery
    public String queryWithObjectArgWithEnum(BeanWithEnum beanWithEnum)
    {
        return beanWithEnum.toString();
    }

}
