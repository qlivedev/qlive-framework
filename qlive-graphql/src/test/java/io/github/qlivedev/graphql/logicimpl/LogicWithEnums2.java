package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLMutation;
import io.github.qlivedev.graphql.beans.AnotherEnum;
import io.github.qlivedev.graphql.beans.BeanWithEnum;
import io.github.qlivedev.graphql.beans.MyEnum;

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
