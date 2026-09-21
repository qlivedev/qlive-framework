package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.beans.FullResponse;

@GraphQLLogic
public class FullDirectiveLogic
{

    @GraphQLQuery( full = true)
    public FullResponse fullQuery()
    {
        final FullResponse response = new FullResponse();
        response.setName("Blafusel");
        response.setNum(12948);
        
        return response;
    }
}
