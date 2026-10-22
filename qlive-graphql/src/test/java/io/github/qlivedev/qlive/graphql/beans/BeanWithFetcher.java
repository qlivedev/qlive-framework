package io.github.qlivedev.qlive.graphql.beans;


import io.github.qlivedev.qlive.graphql.TestFetcher;
import io.github.qlivedev.qlive.api.annotation.GraphQLFetcher;

public class BeanWithFetcher
{
    private String value;


    @GraphQLFetcher(value = TestFetcher.class, data = "test")
    public String getValue()
    {
        return value;
    }


    public void setValue(String value)
    {
        this.value = value;
    }
}
