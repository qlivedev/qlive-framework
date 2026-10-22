package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.graphql.testdomain.tables.pojos.SourceOne;
import io.github.qlivedev.qlive.graphql.util.Paged;

import java.util.Collections;

@QLiveLogic
public class DegenerifyDBLogic
{
    @QLiveQuery
    public Paged<SourceOne> sourceOnes()
    {
        return new Paged<>(Collections.emptyList(),0);
    }
}
