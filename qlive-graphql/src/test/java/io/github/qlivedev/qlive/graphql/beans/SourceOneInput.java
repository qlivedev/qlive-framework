package io.github.qlivedev.qlive.graphql.beans;

import io.github.qlivedev.qlive.graphql.testdomain.tables.pojos.SourceOne;

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
