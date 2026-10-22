package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.GraphQLLogic;
import io.github.qlivedev.qlive.api.annotation.GraphQLQuery;
import io.github.qlivedev.qlive.graphql.beans.IgnoredPropsBean;

@GraphQLLogic
public class IgnoredPropsLogic
{
    @GraphQLQuery
    public IgnoredPropsBean igPropBean()
    {
        return null;
    }
}
