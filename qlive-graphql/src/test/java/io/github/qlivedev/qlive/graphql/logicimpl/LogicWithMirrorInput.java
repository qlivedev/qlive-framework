package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.graphql.testdomain.tables.pojos.SourceOne;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@QLiveLogic
public class LogicWithMirrorInput
{
    private final static Logger log = LoggerFactory.getLogger(LogicWithMirrorInput.class);

    @QLiveQuery
    public boolean queryWithMirrorInput(SourceOne inputOne)
    {
        return true;
    }

}
