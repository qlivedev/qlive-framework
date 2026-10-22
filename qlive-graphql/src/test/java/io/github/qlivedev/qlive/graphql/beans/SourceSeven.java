package io.github.qlivedev.qlive.graphql.beans;

import io.github.qlivedev.qlive.api.annotation.GraphQLComputed;

public class SourceSeven
    extends io.github.qlivedev.qlive.graphql.testdomain.tables.pojos.SourceSeven
{

    @GraphQLComputed
    public String getConcat()
    {
        return this.getId() + ":" + this.getTarget();
    }
}
