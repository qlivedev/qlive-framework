package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.graphql.beans.Payload;
import io.github.qlivedev.qlive.graphql.util.Paged;

import java.util.ArrayList;
import java.util.List;

@QLiveLogic
public class DegenerifyLogic
{
    @QLiveQuery
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
