package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveMutation;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.api.annotation.QLiveTypeParam;
import io.github.qlivedev.qlive.graphql.beans.Payload;
import io.github.qlivedev.qlive.graphql.util.Paged;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@QLiveLogic
public class NotNullQueryLogic
{
    private final static Logger log = LoggerFactory.getLogger(NotNullQueryLogic.class);


    @QLiveQuery
    public <T> @NotNull Paged<T> query(
        @QLiveTypeParam( types = { TypeA.class, TypeB.class }, typeNamePattern = "*Document") Class<T> cls,
        String name
    ) throws IllegalAccessException, InstantiationException
    {
        return new Paged<>();
    }

    @QLiveQuery
    public <T> @NotNull Payload q2(
        String name
    )
    {
        return new Payload();
    }

    @QLiveMutation
    public <T> @NotNull Paged<T> mutation(
        @QLiveTypeParam( types = { TypeA.class, TypeB.class }, typeNamePattern = "*Document") Class<T> cls,
        String name
    ) throws IllegalAccessException, InstantiationException
    {
        return new Paged<>();
    }

    @QLiveMutation
    public <T> @NotNull Payload m2(
        String name
    )
    {
        return new Payload();
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
