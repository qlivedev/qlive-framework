package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveMutation;
import io.github.qlivedev.qlive.graphql.beans.AnnotatedBean;
import io.github.qlivedev.qlive.graphql.beans.AnnotatedBeanInput;

@QLiveLogic
public class LogicWithAnnotated
{

    @QLiveMutation
    public AnnotatedBean annotated(AnnotatedBeanInput in)
    {
        return null;
    }
}
