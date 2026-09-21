package io.github.qlivedev.graphql.beans;

import io.github.qlivedev.graphql.annotation.GraphQLField;

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
