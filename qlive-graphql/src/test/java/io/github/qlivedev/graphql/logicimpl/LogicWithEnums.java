package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.beans.BeanWithEnum;
import io.github.qlivedev.graphql.beans.MyEnum;

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
