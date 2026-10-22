package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.GraphQLLogic;
import io.github.qlivedev.qlive.api.annotation.GraphQLQuery;
import io.github.qlivedev.qlive.graphql.beans.Container;
import io.github.qlivedev.qlive.graphql.beans.Payload;

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
