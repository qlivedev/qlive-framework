package io.github.qlivedev.qlive.graphql.beans;

import io.github.qlivedev.qlive.api.annotation.GraphQLField;

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


    @GraphQLField
    public String getModifiedValue(String arg, @GraphQLField(defaultValue = "12") int num)
    {

        return value + ":" + arg + ":" + num;
    }


    @GraphQLField
    public GetterArgIntroducedBean getIntroduced(String arg)
    {

        return new GetterArgIntroducedBean(arg);
    }
}

