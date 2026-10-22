package io.github.qlivedev.qlive.graphql.beans;


import io.github.qlivedev.qlive.graphql.testdomain.tables.pojos.SourceTwo;

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
