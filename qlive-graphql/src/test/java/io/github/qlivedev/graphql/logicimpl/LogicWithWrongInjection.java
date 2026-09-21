package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.testdomain.tables.SourceOne;

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
