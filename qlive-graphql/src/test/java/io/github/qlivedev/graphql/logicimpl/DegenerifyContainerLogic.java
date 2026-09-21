package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.beans.Container;
import io.github.qlivedev.graphql.beans.Payload;

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
