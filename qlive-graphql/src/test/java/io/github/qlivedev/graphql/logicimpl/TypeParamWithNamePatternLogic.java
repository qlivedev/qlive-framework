package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.annotation.GraphQLTypeParam;
import io.github.qlivedev.graphql.beans.ComplexInput;
import io.github.qlivedev.graphql.util.Paged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@GraphQLLogic
public class TypeParamWithNamePatternLogic
{
    private final static Logger log = LoggerFactory.getLogger(TypeParamWithNamePatternLogic.class);


    @GraphQLQuery
    public <T> T query(
        @GraphQLTypeParam( types = { TypeA.class, TypeB.class }, namePattern = "*Query") Class<T> cls,
        ComplexInput complexInput
    ) throws IllegalAccessException, InstantiationException
    {
        return null;
    }


    @GraphQLQuery
    public <T> Paged<T> q2(
        @GraphQLTypeParam( types = { TypeA.class, TypeB.class }, typeNamePattern = "*Document") Class<T> cls,
        String name
    ) throws IllegalAccessException, InstantiationException
    {
        return null;
    }

    @GraphQLQuery
    public <T> Paged<T> q3(
        @GraphQLTypeParam( types = { TypeA.class, TypeB.class }, namePattern = "Q3*") Class<T> cls,
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
