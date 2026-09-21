package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.beans.SourceOne;

@GraphQLLogic
public class ImplicitOverrideNonInputLogic
{
    @GraphQLQuery
    public boolean queryThatOverrides(SourceOne sourceOneInput)
    {
        return true;
    }
}
