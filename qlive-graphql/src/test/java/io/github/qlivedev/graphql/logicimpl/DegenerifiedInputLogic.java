package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLField;
import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLMutation;
import io.github.qlivedev.graphql.beans.Payload;
import io.github.qlivedev.graphql.util.Paged;

@GraphQLLogic
public class DegenerifiedInputLogic
{

    // Using "Paged" as input for convenience, not because it makes a lot of sense to submit paged data
    @GraphQLMutation
    public String mutationWithDegenerifiedInput(

        @GraphQLField(notNull = true)
        Paged<Payload> pagedPayload

    )
    {
        StringBuilder buff = new StringBuilder();

        for (Payload payload : pagedPayload.getRows())
        {
            buff.append(payload.getName())
                .append(":")
                .append(payload.getNum())
                .append("|");
        }

        return buff.toString();
    }
}
