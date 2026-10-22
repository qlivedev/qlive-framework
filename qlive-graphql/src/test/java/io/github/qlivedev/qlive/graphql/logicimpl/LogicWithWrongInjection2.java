package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.graphql.testdomain.tables.records.SourceOneRecord;

/* BOOM! */

@QLiveLogic
public class LogicWithWrongInjection2
{
    @QLiveQuery
    public boolean wrong(SourceOneRecord sourceOne)
    {
        return true;
    }
}
