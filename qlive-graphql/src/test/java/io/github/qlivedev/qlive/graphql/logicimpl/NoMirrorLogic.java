package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.GraphQLLogic;
import io.github.qlivedev.qlive.api.annotation.GraphQLQuery;
import io.github.qlivedev.qlive.graphql.beans.NoMirrorBean;

@GraphQLLogic
public class NoMirrorLogic
{
    @GraphQLQuery
    public NoMirrorBean getValue()
    {
        return null;
    }
}
