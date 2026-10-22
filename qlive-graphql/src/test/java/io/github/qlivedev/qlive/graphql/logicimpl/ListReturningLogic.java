package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.GraphQLLogic;
import io.github.qlivedev.qlive.api.annotation.GraphQLQuery;
import io.github.qlivedev.qlive.graphql.testdomain.tables.pojos.SourceThree;

import java.util.Collections;
import java.util.List;

@GraphQLLogic
public class ListReturningLogic
{
    @GraphQLQuery
    public List<SourceThree> listOfThrees()
    {
        return Collections.emptyList();
    }

}
