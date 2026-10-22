package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.GraphQLLogic;
import io.github.qlivedev.qlive.api.annotation.GraphQLMutation;
import io.github.qlivedev.qlive.graphql.beans.AnotherEnum;
import io.github.qlivedev.qlive.graphql.beans.BeanWithEnum;
import io.github.qlivedev.qlive.graphql.beans.MyEnum;

@GraphQLLogic
public class LogicWithEnums2
{

    @GraphQLMutation
    public MyEnum enumMutation()
    {
        return MyEnum.B;
    }

    @GraphQLMutation
    public BeanWithEnum objectWithEnumMutation()
    {
        final BeanWithEnum beanWithEnum = new BeanWithEnum();
        beanWithEnum.setAnotherEnum(AnotherEnum.Z);
        return beanWithEnum;
    }
}
