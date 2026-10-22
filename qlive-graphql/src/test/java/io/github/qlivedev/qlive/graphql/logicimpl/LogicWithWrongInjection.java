package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.GraphQLLogic;
import io.github.qlivedev.qlive.api.annotation.GraphQLQuery;
import io.github.qlivedev.qlive.graphql.testdomain.tables.SourceOne;

/* BOOM */

@GraphQLLogic
public class LogicWithWrongInjection
{
    @GraphQLQuery
    public boolean wrong(SourceOne sourceOne)
    {
        return true;
    }
}
