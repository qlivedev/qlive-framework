package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLMutation;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.annotation.GraphQLTypeParam;
import io.github.qlivedev.graphql.beans.DocumentedBean;
import io.github.qlivedev.graphql.beans.DocumentedEnum;
import io.github.qlivedev.graphql.testdomain.tables.pojos.Foo;
import io.github.qlivedev.graphql.util.Paged;
import graphql.schema.DataFetchingEnvironment;

import java.util.Collections;

/**
 * Target to be analyzed by DocsExtractorTest
 *
 * @see io.github.qlivedev.graphql.docs.DocsExtractorTest
 */
@GraphQLLogic
public class DocumentedLogic
{
    /**
     * A minimal GraphQL query
     *
     * @return always true
     */
    @GraphQLQuery
    public boolean query()
    {
        return true;
    }

    /**
     * Another query
     */
    @GraphQLQuery(value = "anotherQuery")
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
    @GraphQLMutation
    public boolean mutation(int foo)
    {
        return false;
    }

    @GraphQLQuery
    public DocumentedEnum enumLogic()
    {
        return null;
    }

    @GraphQLQuery
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
    @GraphQLQuery
    public <T> Paged<T> genericPaged(
        @GraphQLTypeParam(
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
