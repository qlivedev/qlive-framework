package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.testdomain.tables.records.SourceOneRecord;

/* BOOM! */

@GraphQLLogic
public class LogicWithWrongInjection2
{
    @GraphQLQuery
    public boolean wrong(SourceOneRecord sourceOne)
    {
        return true;
    }
}
