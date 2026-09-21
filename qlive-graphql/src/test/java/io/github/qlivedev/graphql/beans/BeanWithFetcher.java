package io.github.qlivedev.graphql.beans;


import io.github.qlivedev.graphql.TestFetcher;
import io.github.qlivedev.graphql.annotation.GraphQLFetcher;

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
