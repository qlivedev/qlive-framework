package io.github.qlivedev.qlive.graphql.beans;

import io.github.qlivedev.qlive.api.annotation.QLiveField;

public class GetterArgBean
{
    private final String value;

    public GetterArgBean(String value)
    {
        this.value = value;
    }


    public String getValue()
    {
        return value;
    }


    @QLiveField
    public String getModifiedValue(String arg, @QLiveField(defaultValue = "12") int num)
    {

        return value + ":" + arg + ":" + num;
    }


    @QLiveField
    public GetterArgIntroducedBean getIntroduced(String arg)
    {

        return new GetterArgIntroducedBean(arg);
    }
}

