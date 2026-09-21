package io.github.qlivedev.graphql.beans;


import io.github.qlivedev.graphql.testdomain.tables.pojos.SourceTwo;

public class SourceTwoInput
    extends SourceTwo
{
    private String foo;


    public String getFoo()
    {
        return foo;
    }


    public void setFoo(String foo)
    {
        this.foo = foo;
    }
}
