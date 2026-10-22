package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.GraphQLLogic;
import io.github.qlivedev.qlive.api.annotation.GraphQLQuery;
import io.github.qlivedev.qlive.graphql.testdomain.tables.pojos.SourceOne;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@GraphQLLogic
public class LogicWithMirrorInput
{
    private final static Logger log = LoggerFactory.getLogger(LogicWithMirrorInput.class);

    @GraphQLQuery
    public boolean queryWithMirrorInput(SourceOne inputOne)
    {
        return true;
    }

}
