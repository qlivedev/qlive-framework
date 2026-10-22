package io.github.qlivedev.qlive.graphql.beans;

import io.github.qlivedev.qlive.api.annotation.GraphQLComputed;

public class TargetSeven
    extends io.github.qlivedev.qlive.graphql.testdomain.tables.pojos.TargetSeven
{

    @GraphQLComputed
    public String getConcat()
    {
        return this.getId() + ":" + this.getName();
    }
}
