package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLMutation;
import io.github.qlivedev.graphql.beans.DependencyBean;

import java.util.Collections;
import java.util.List;

@GraphQLLogic
public class LogicWithGenerics
{
    @GraphQLMutation
    public List<Integer> mutationReturningListOfInts()
    {
        return Collections.emptyList();
    }

    @GraphQLMutation
    public boolean mutationWithIntListParam(List<Integer> args)
    {
        return true;
    }

    @GraphQLMutation
    public List<DependencyBean> mutationReturningListOfObject()
    {
        return Collections.emptyList();
    }

    @GraphQLMutation
    public boolean mutationWithObjectListParam(List<DependencyBean> args)
    {
        return true;
    }
}
