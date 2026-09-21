package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.beans.Payload;
import io.github.qlivedev.graphql.util.Paged;

import java.util.ArrayList;
import java.util.List;

@GraphQLLogic
public class DegenerifyLogic
{
    @GraphQLQuery
    public Paged<Payload> getPayload()
    {
        final Paged<Payload> paged = new Paged<>();

        List<Payload> rows = new ArrayList<>();

        rows.add(new Payload("aaa", 5));
        rows.add(new Payload("bbb", 7));

        paged.setRows(rows);
        paged.setRowCount(2);

        return paged;
    }

}
