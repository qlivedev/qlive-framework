package io.github.qlivedev.qlive.api.action;

import java.util.List;
import java.util.stream.Collectors;

/// An action refusing to act, thrown by the framework when a subject's version or a rule fails, or by the action
/// method itself.
///
/// The framework catches it around the method, rolls the action's transaction back, and answers the mutation
/// with one GraphQL error per refusal and no data. A method throwing it gets the same treatment, so the client
/// sees one kind of refusal whatever produced it.
public class ActionRefusedException
    extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    private final List<Refusal> refusals;


    public ActionRefusedException(List<Refusal> refusals)
    {
        super(describe(refusals));

        if (refusals == null || refusals.isEmpty())
        {
            throw new IllegalArgumentException("A refusal needs at least one reason");
        }

        this.refusals = List.copyOf(refusals);
    }


    /// Refuses for one reason.
    ///
    /// @param messageKey   i18n key of the message
    /// @param field        path of the blamed field or `null`
    public ActionRefusedException(String messageKey, String field)
    {
        this(List.of(Refusal.rule(messageKey, field)));
    }


    public List<Refusal> getRefusals()
    {
        return refusals;
    }


    private static String describe(List<Refusal> refusals)
    {
        if (refusals == null)
        {
            return null;
        }

        return "Action refused: " + refusals.stream()
            .map(r -> r.code() + " " + r.messageKey() + (r.field() != null ? " (" + r.field() + ")" : ""))
            .collect(Collectors.joining(", "));
    }
}
