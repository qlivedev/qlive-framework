package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.beans.SourceOneInput;

@GraphQLLogic
public class ImplicitOverrideLogic
{
    @GraphQLQuery
    public boolean queryThatOverrides(SourceOneInput sourceOneInput)
    {
        return true;
    }
}
