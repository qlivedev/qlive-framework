package io.github.qlivedev.qlive.graphql.logic;

/// What stands between GraphQL and an action method: the transaction, the subjects' rows and their versions, and
/// the rules.
///
/// The domain is assembled below the layer that has transactions and the query engine, so it holds the actions
/// and the runtime binds an executor to each of them (see {@link Action#bind(ActionExecutor)}).
public interface ActionExecutor
{
    /// Executes an action.
    ///
    /// @param action       the action
    /// @param arguments    one value per method parameter; a subject's is its {@link SubjectRef} or list of them
    ///
    /// @return what the action method returned, and what the response carries beside it
    ///
    /// @throws io.github.qlivedev.qlive.api.action.ActionRefusedException where the action refuses
    ActionResult execute(Action action, Object[] arguments);
}
