package io.github.qlivedev.graphql.beans;

import io.github.qlivedev.graphql.testdomain.tables.pojos.SourceOne;

public class SourceOneInput
    extends SourceOne
{
    private String extra;


    public String getExtra()
    {
        return extra;
    }


    public void setExtra(String extra)
    {
        this.extra = extra;
    }
}
