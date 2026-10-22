package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.GraphQLLogic;
import io.github.qlivedev.qlive.api.annotation.GraphQLQuery;
import io.github.qlivedev.qlive.graphql.beans.SourceOneInput;

@GraphQLLogic
public class ImplicitOverrideLogic
{
    @GraphQLQuery
    public boolean queryThatOverrides(SourceOneInput sourceOneInput)
    {
        return true;
    }
}
