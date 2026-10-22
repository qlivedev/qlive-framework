package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.graphql.beans.Container;
import io.github.qlivedev.qlive.graphql.beans.Payload;

@QLiveLogic
public class DegenerifiedContainerLogic
{
    @QLiveQuery

    public String containerQuery(Container<Payload> container)
    {
        final Payload payload = container.getValue();
        return payload.getName() + ":" + payload.getNum() + ":" + container.getNum();
    }
}
