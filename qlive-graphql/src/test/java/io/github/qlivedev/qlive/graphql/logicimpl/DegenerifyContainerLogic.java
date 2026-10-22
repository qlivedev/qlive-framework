package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.GraphQLLogic;
import io.github.qlivedev.qlive.api.annotation.GraphQLQuery;
import io.github.qlivedev.qlive.graphql.beans.Container;
import io.github.qlivedev.qlive.graphql.beans.Payload;

@GraphQLLogic
public class DegenerifyContainerLogic
{
    @GraphQLQuery
    public Container<Payload> queryContainer()
    {
        final Container<Payload> container = new Container<>();

        container.setValue(new Payload("DDD", 444));
        container.setNum(555);
        return container;
    }
}
