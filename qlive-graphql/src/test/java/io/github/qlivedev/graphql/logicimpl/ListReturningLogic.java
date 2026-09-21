package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.testdomain.tables.pojos.SourceThree;

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
