package io.github.qlivedev.qlive.graphql.beans;

import io.github.qlivedev.qlive.api.annotation.GraphQLField;

public class AnnotatedBeanInput
{
    private long value;


    @GraphQLField( type = "Currency")
    public long getValue()
    {
        return value;
    }


    public void setValue(long value)
    {
        this.value = value;
    }
}
