package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.GraphQLLogic;
import io.github.qlivedev.qlive.api.annotation.GraphQLMutation;
import io.github.qlivedev.qlive.api.annotation.GraphQLQuery;
import io.github.qlivedev.qlive.graphql.beans.MarkdownDocumentedBean;

/// Target to be analyzed by DocsExtractorTest
///
/// @see io.github.qlivedev.qlive.graphql.docs.DocsExtractorTest
@GraphQLLogic
public class MarkdownDocumentedLogic
{
    /// A minimal markdown GraphQL query
    ///
    /// @return always true
    @GraphQLQuery
    public boolean markdownQuery()
    {
        return true;
    }


    /// Another markdown query
    @GraphQLQuery(value = "anotherMarkdownQuery")
    public MarkdownDocumentedBean markdownQuery2(MarkdownDocumentedBean bean)
    {
        return bean;
    }


    /// A markdown GraphQL mutation
    ///
    /// Supports:
    ///
    /// * one
    /// * two
    ///
    /// @param foo   foo param desc
    /// @return always false
    @GraphQLMutation
    public boolean markdownMutation(int foo)
    {
        return false;
    }
}
