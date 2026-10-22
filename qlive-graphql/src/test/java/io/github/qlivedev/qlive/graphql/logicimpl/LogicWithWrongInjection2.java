package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.GraphQLLogic;
import io.github.qlivedev.qlive.api.annotation.GraphQLQuery;
import io.github.qlivedev.qlive.graphql.testdomain.tables.records.SourceOneRecord;

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
