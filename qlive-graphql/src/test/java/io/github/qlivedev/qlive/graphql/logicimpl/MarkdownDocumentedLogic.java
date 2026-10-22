package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveAction;
import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveMutation;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.graphql.beans.MarkdownDocumentedBean;

/// Target to be analyzed by DocsExtractorTest
///
/// @see io.github.qlivedev.qlive.graphql.docs.DocsExtractorTest
@QLiveLogic
public class MarkdownDocumentedLogic
{
    /// A minimal markdown GraphQL query
    ///
    /// @return always true
    @QLiveQuery
    public boolean markdownQuery()
    {
        return true;
    }


    /// Another markdown query
    @QLiveQuery(value = "anotherMarkdownQuery")
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
    @QLiveMutation
    public boolean markdownMutation(int foo)
    {
        return false;
    }


    /// A markdown action
    ///
    /// @param foo   foo param desc
    /// @return always false
    @QLiveAction
    public boolean markdownAction(int foo)
    {
        return false;
    }
}
