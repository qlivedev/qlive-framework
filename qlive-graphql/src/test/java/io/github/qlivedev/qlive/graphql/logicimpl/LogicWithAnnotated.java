package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.GraphQLLogic;
import io.github.qlivedev.qlive.api.annotation.GraphQLMutation;
import io.github.qlivedev.qlive.graphql.beans.AnnotatedBean;
import io.github.qlivedev.qlive.graphql.beans.AnnotatedBeanInput;

@GraphQLLogic
public class LogicWithAnnotated
{

    @GraphQLMutation
    public AnnotatedBean annotated(AnnotatedBeanInput in)
    {
        return null;
    }
}
