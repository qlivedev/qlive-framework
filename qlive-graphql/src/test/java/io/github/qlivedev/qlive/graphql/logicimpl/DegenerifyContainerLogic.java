package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.graphql.beans.Container;
import io.github.qlivedev.qlive.graphql.beans.Payload;

@QLiveLogic
public class DegenerifyContainerLogic
{
    @QLiveQuery
    public Container<Payload> queryContainer()
    {
        final Container<Payload> container = new Container<>();

        container.setValue(new Payload("DDD", 444));
        container.setNum(555);
        return container;
    }
}
