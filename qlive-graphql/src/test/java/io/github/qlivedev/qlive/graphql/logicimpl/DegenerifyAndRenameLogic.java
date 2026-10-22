package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.GraphQLLogic;
import io.github.qlivedev.qlive.api.annotation.GraphQLQuery;
import io.github.qlivedev.qlive.api.annotation.ResolvedGenericType;
import io.github.qlivedev.qlive.graphql.beans.AnnotatedPayload;
import io.github.qlivedev.qlive.graphql.util.Paged;

import java.util.ArrayList;
import java.util.List;

@GraphQLLogic
public class DegenerifyAndRenameLogic
{

    @GraphQLQuery
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
