package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.beans.IgnoredPropsBean;

@GraphQLLogic
public class IgnoredPropsLogic
{
    @GraphQLQuery
    public IgnoredPropsBean igPropBean()
    {
        return null;
    }
}
