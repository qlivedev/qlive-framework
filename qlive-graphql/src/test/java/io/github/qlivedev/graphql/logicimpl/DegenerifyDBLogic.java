package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.testdomain.tables.pojos.SourceOne;
import io.github.qlivedev.graphql.util.Paged;

import java.util.Collections;

@GraphQLLogic
public class DegenerifyDBLogic
{
    @GraphQLQuery
    public Paged<SourceOne> sourceOnes()
    {
        return new Paged<>(Collections.emptyList(),0);
    }
}
