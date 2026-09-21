package io.github.qlivedev.graphql.beans;

import io.github.qlivedev.graphql.annotation.GraphQLComputed;

public class TargetSeven
    extends io.github.qlivedev.graphql.testdomain.tables.pojos.TargetSeven
{

    @GraphQLComputed
    public String getConcat()
    {
        return this.getId() + ":" + this.getName();
    }
}
