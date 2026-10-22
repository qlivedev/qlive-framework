package io.github.qlivedev.qlive.graphql.beans;

import io.github.qlivedev.qlive.api.annotation.QLiveComputed;

public class SourceSeven
    extends io.github.qlivedev.qlive.graphql.testdomain.tables.pojos.SourceSeven
{

    @QLiveComputed
    public String getConcat()
    {
        return this.getId() + ":" + this.getTarget();
    }
}
