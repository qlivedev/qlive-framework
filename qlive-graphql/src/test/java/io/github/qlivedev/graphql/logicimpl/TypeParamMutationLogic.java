package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLMutation;
import io.github.qlivedev.graphql.annotation.GraphQLTypeParam;
import io.github.qlivedev.graphql.beans.ComplexInput;
import io.github.qlivedev.graphql.beans.Container;
import io.github.qlivedev.util.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@GraphQLLogic
public class TypeParamMutationLogic
{
    private final static Logger log = LoggerFactory.getLogger(TypeParamMutationLogic.class);


    @GraphQLMutation
    public <T> T mutate(
        @GraphQLTypeParam(types = { TypeA.class, TypeB.class }) Class<T> cls,
        ComplexInput complexInput
    ) throws IllegalAccessException, InstantiationException
    {

        final T bean = cls.newInstance();

        JSONUtil.DEFAULT_UTIL.setProperty(bean,"value", complexInput.getValue() + "/" + complexInput.getNum());


        return bean;
    }


    @GraphQLMutation
    public <T> Container<T> mutateContainer(
        @GraphQLTypeParam(types = { TypeA.class, TypeB.class }) Class<T> cls,
        ComplexInput complexInput
    ) throws IllegalAccessException, InstantiationException
    {

        final T bean = cls.newInstance();

        JSONUtil.DEFAULT_UTIL.setProperty(bean,"value", complexInput.getValue() + "/" + complexInput.getNum());


        final Container<T> container = new Container<>();
        container.setValue(bean);
        container.setNum(123);
        return container;
    }


    @GraphQLMutation
    public <T> List<T> mutateList(
        @GraphQLTypeParam(types = { TypeA.class, TypeB.class }) Class<T> cls,
        ComplexInput complexInput
    ) throws IllegalAccessException, InstantiationException
    {

        final ArrayList<T> list = new ArrayList<>();

        final T bean = cls.newInstance();
        JSONUtil.DEFAULT_UTIL.setProperty(bean,"value", complexInput.getValue() + "...");
        list.add(bean);

        final T bean2 = cls.newInstance();
        JSONUtil.DEFAULT_UTIL.setProperty(bean2,"value", "..." + complexInput.getNum());
        list.add(bean2);

        return list;
    }


    public final static class TypeA
    {
        private String value;


        public String getValue()
        {
            return value;
        }


        public void setValue(String value)
        {
            this.value = value;
        }
    }

    public final static class TypeB
    {
        private String value;


        public String getValue()
        {
            return value;
        }


        public void setValue(String value)
        {
            this.value = value;
        }
    }

}
