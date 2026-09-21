package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLMutation;
import io.github.qlivedev.graphql.beans.AnnotatedBean;
import io.github.qlivedev.graphql.beans.AnnotatedBeanInput;

@GraphQLLogic
public class LogicWithAnnotated
{

    @GraphQLMutation
    public AnnotatedBean annotated(AnnotatedBeanInput in)
    {
        return null;
    }
}
