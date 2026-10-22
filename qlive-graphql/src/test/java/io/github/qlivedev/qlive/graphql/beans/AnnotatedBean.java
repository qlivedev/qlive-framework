package io.github.qlivedev.qlive.graphql.beans;

import io.github.qlivedev.qlive.api.annotation.QLiveField;

public class AnnotatedBean
{
    private long value;


    @QLiveField( type = "Currency")
    public long getValue()
    {
        return value;
    }


    public void setValue(long value)
    {
        this.value = value;
    }
}
