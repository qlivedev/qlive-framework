package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveMutation;
import io.github.qlivedev.qlive.graphql.beans.Container;
import io.github.qlivedev.qlive.graphql.beans.ContainerProp;
import io.github.qlivedev.qlive.graphql.beans.Payload;

/**
 * Tests a generic container embedding another generic container directly as prop.
 * (<code>Container&lt;T&gt;</code>, not <code>T</code> or <code>List&lt;T&gt</code>
 */
@QLiveLogic
public class DoubleDegenerificationLogic
{
    @QLiveMutation
    public String mutationWithDD(ContainerProp<Payload> c)
    {
        final Container<Payload> container = c.getValue();
        final Payload payload = container.getValue();
        return "[" + payload.getName() + ":" + payload.getNum() + ":" + container.getNum() + "]";
    }
}
