package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.graphql.testdomain.tables.pojos.SourceThree;

import java.util.Collections;
import java.util.List;

@QLiveLogic
public class ListReturningLogic
{
    @QLiveQuery
    public List<SourceThree> listOfThrees()
    {
        return Collections.emptyList();
    }

}
