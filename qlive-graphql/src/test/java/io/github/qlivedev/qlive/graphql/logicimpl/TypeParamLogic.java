package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.api.annotation.QLiveTypeParam;
import io.github.qlivedev.qlive.graphql.beans.ComplexInput;
import io.github.qlivedev.qlive.graphql.beans.Container;
import io.github.qlivedev.qlive.api.json.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@QLiveLogic
public class TypeParamLogic
{
    private final static Logger log = LoggerFactory.getLogger(TypeParamLogic.class);


    @QLiveQuery
    public <T> T query(
        @QLiveTypeParam(types = { TypeA.class, TypeB.class }) Class<T> cls,
        ComplexInput complexInput
    ) throws IllegalAccessException, InstantiationException
    {

        final T bean = cls.newInstance();

        JSONUtil.DEFAULT_UTIL.setProperty(bean,"value", complexInput.getValue() + "/" + complexInput.getNum());


        return bean;
    }


    @QLiveQuery
    public <T> Container<T> queryContainer(
        @QLiveTypeParam(types = { TypeA.class, TypeB.class }) Class<T> cls,
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


    @QLiveQuery
    public <T> List<T> queryList(
        @QLiveTypeParam(types = { TypeA.class, TypeB.class }) Class<T> cls,
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
