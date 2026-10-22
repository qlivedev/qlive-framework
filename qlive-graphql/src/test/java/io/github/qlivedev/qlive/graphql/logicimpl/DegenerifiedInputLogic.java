package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveField;
import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveMutation;
import io.github.qlivedev.qlive.graphql.beans.Payload;
import io.github.qlivedev.qlive.graphql.util.Paged;

@QLiveLogic
public class DegenerifiedInputLogic
{

    // Using "Paged" as input for convenience, not because it makes a lot of sense to submit paged data
    @QLiveMutation
    public String mutationWithDegenerifiedInput(

        @QLiveField(notNull = true)
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
