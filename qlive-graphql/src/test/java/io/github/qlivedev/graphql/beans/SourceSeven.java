package io.github.qlivedev.graphql.beans;

import io.github.qlivedev.graphql.annotation.GraphQLComputed;

public class SourceSeven
    extends io.github.qlivedev.graphql.testdomain.tables.pojos.SourceSeven
{

    @GraphQLComputed
    public String getConcat()
    {
        return this.getId() + ":" + this.getTarget();
    }
}
