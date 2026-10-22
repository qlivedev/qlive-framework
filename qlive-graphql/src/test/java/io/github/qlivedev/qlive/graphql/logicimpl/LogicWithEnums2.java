package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveMutation;
import io.github.qlivedev.qlive.graphql.beans.AnotherEnum;
import io.github.qlivedev.qlive.graphql.beans.BeanWithEnum;
import io.github.qlivedev.qlive.graphql.beans.MyEnum;

@QLiveLogic
public class LogicWithEnums2
{

    @QLiveMutation
    public MyEnum enumMutation()
    {
        return MyEnum.B;
    }

    @QLiveMutation
    public BeanWithEnum objectWithEnumMutation()
    {
        final BeanWithEnum beanWithEnum = new BeanWithEnum();
        beanWithEnum.setAnotherEnum(AnotherEnum.Z);
        return beanWithEnum;
    }
}
