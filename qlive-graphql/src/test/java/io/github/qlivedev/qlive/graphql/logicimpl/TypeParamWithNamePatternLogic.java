package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.api.annotation.QLiveTypeParam;
import io.github.qlivedev.qlive.graphql.beans.ComplexInput;
import io.github.qlivedev.qlive.graphql.util.Paged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@QLiveLogic
public class TypeParamWithNamePatternLogic
{
    private final static Logger log = LoggerFactory.getLogger(TypeParamWithNamePatternLogic.class);


    @QLiveQuery
    public <T> T query(
        @QLiveTypeParam( types = { TypeA.class, TypeB.class }, namePattern = "*Query") Class<T> cls,
        ComplexInput complexInput
    ) throws IllegalAccessException, InstantiationException
    {
        return null;
    }


    @QLiveQuery
    public <T> Paged<T> q2(
        @QLiveTypeParam( types = { TypeA.class, TypeB.class }, typeNamePattern = "*Document") Class<T> cls,
        String name
    ) throws IllegalAccessException, InstantiationException
    {
        return null;
    }

    @QLiveQuery
    public <T> Paged<T> q3(
        @QLiveTypeParam( types = { TypeA.class, TypeB.class }, namePattern = "Q3*") Class<T> cls,
        String name
    ) throws IllegalAccessException, InstantiationException
    {
        return null;
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
