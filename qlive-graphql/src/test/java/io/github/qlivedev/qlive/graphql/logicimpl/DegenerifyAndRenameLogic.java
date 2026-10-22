package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.api.annotation.ResolvedGenericType;
import io.github.qlivedev.qlive.graphql.beans.AnnotatedPayload;
import io.github.qlivedev.qlive.graphql.util.Paged;

import java.util.ArrayList;
import java.util.List;

@QLiveLogic
public class DegenerifyAndRenameLogic
{

    @QLiveQuery
    @ResolvedGenericType("PagedAndRenamed")
    public Paged<AnnotatedPayload> getPayload()
    {
        final Paged<AnnotatedPayload> paged = new Paged<>();

        List<AnnotatedPayload> rows = new ArrayList<>();

        rows.add(new AnnotatedPayload("aaa", 555));
        rows.add(new AnnotatedPayload("bbb", 7777));

        paged.setRows(rows);
        paged.setRowCount(2);

        return paged;
    }

}
