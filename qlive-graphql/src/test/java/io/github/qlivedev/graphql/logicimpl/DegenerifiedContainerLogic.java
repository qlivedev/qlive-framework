package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.beans.Container;
import io.github.qlivedev.graphql.beans.Payload;

@GraphQLLogic
public class DegenerifiedContainerLogic
{
    @GraphQLQuery

    public String containerQuery(Container<Payload> container)
    {
        final Payload payload = container.getValue();
        return payload.getName() + ":" + payload.getNum() + ":" + container.getNum();
    }
}
