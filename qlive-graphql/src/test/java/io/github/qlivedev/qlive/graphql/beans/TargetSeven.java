package io.github.qlivedev.qlive.graphql.beans;

import io.github.qlivedev.qlive.api.annotation.QLiveComputed;

public class TargetSeven
    extends io.github.qlivedev.qlive.graphql.testdomain.tables.pojos.TargetSeven
{

    @QLiveComputed
    public String getConcat()
    {
        return this.getId() + ":" + this.getName();
    }
}
