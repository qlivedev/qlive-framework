package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.GraphQLLogic;
import io.github.qlivedev.qlive.api.annotation.GraphQLQuery;
import io.github.qlivedev.qlive.graphql.testdomain.tables.pojos.SourceOne;
import io.github.qlivedev.qlive.graphql.util.Paged;

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
