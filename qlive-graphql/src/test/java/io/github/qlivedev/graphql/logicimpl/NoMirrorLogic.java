package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.beans.NoMirrorBean;

@GraphQLLogic
public class NoMirrorLogic
{
    @GraphQLQuery
    public NoMirrorBean getValue()
    {
        return null;
    }
}
