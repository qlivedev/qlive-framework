package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveMutation;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.api.annotation.QLiveTypeParam;
import io.github.qlivedev.qlive.graphql.beans.DocumentedBean;
import io.github.qlivedev.qlive.graphql.beans.DocumentedEnum;
import io.github.qlivedev.qlive.graphql.testdomain.tables.pojos.Foo;
import io.github.qlivedev.qlive.graphql.util.Paged;
import graphql.schema.DataFetchingEnvironment;

import java.util.Collections;

/**
 * Target to be analyzed by DocsExtractorTest
 *
 * @see io.github.qlivedev.qlive.graphql.docs.DocsExtractorTest
 */
@QLiveLogic
public class DocumentedLogic
{
    /**
     * A minimal GraphQL query
     *
     * @return always true
     */
    @QLiveQuery
    public boolean query()
    {
        return true;
    }

    /**
     * Another query
     */
    @QLiveQuery(value = "anotherQuery")
    public DocumentedBean query2(DocumentedBean documentedBean)
    {
        return documentedBean;
    }

    /**
     * A GraphQL mutation
     *
     * @param foo   foo param desc
     *
     * @return always true
     */
    @QLiveMutation
    public boolean mutation(int foo)
    {
        return false;
    }

    @QLiveQuery
    public DocumentedEnum enumLogic()
    {
        return null;
    }

    @QLiveQuery
    public Paged<Foo> fooPaged()
    {
        return null;
    }


    /**
     * Paginated result of type [T]
     * 
     * @param type
     * @param env
     * @param <T>
     * @return
     */
    @QLiveQuery
    public <T> Paged<T> genericPaged(
        @QLiveTypeParam(
            types = {
                Foo.class
            }
        )
            Class<T> type,
        DataFetchingEnvironment env
    )
    {
        return new Paged<>(Collections.emptyList(), 0);
    }
}
