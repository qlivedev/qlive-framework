package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLMutation;
import io.github.qlivedev.graphql.beans.Container;
import io.github.qlivedev.graphql.beans.ContainerProp;
import io.github.qlivedev.graphql.beans.Payload;

/**
 * Tests a generic container embedding another generic container directly as prop.
 * (<code>Container&lt;T&gt;</code>, not <code>T</code> or <code>List&lt;T&gt</code>
 */
@GraphQLLogic
public class DoubleDegenerificationLogic
{
    @GraphQLMutation
    public String mutationWithDD(ContainerProp<Payload> c)
    {
        final Container<Payload> container = c.getValue();
        final Payload payload = container.getValue();
        return "[" + payload.getName() + ":" + payload.getNum() + ":" + container.getNum() + "]";
    }
}
