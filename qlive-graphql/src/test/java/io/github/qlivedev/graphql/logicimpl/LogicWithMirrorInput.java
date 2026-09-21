package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.testdomain.tables.pojos.SourceOne;
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
