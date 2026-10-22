package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.graphql.testdomain.tables.SourceOne;

/* BOOM */

@QLiveLogic
public class LogicWithWrongInjection
{
    @QLiveQuery
    public boolean wrong(SourceOne sourceOne)
    {
        return true;
    }
}
