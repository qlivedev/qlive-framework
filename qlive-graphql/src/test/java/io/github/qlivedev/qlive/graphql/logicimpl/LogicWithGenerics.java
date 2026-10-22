package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveMutation;
import io.github.qlivedev.qlive.graphql.beans.DependencyBean;

import java.util.Collections;
import java.util.List;

@QLiveLogic
public class LogicWithGenerics
{
    @QLiveMutation
    public List<Integer> mutationReturningListOfInts()
    {
        return Collections.emptyList();
    }

    @QLiveMutation
    public boolean mutationWithIntListParam(List<Integer> args)
    {
        return true;
    }

    @QLiveMutation
    public List<DependencyBean> mutationReturningListOfObject()
    {
        return Collections.emptyList();
    }

    @QLiveMutation
    public boolean mutationWithObjectListParam(List<DependencyBean> args)
    {
        return true;
    }
}
