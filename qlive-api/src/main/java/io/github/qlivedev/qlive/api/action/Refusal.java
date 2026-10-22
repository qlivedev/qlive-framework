package io.github.qlivedev.qlive.api.action;

/// One reason an action was refused, which becomes one GraphQL error.
///
/// @param code         what kind of refusal it is: {@link #RULE} or {@link #CONFLICT}, or the application's own
/// @param messageKey   i18n key of the message the client shows
/// @param field        path of the blamed field, starting with a parameter name -- `bar.description`,
///                     `bars.2.description`, `comment` -- or `null` where the refusal blames no field
/// @param errorClass   class a layout can react to, or `null`
public record Refusal(String code, String messageKey, String field, String errorClass)
{
    /// A rule did not hold.
    public final static String RULE = "RULE";

    /// A subject row is no longer in the state the user saw: its version changed, or it is gone.
    public final static String CONFLICT = "CONFLICT";


    /// A refusal of the method's own, for a check no rule can express.
    ///
    /// @param messageKey   i18n key of the message
    /// @param field        path of the blamed field or `null`
    ///
    /// @return refusal
    public static Refusal rule(String messageKey, String field)
    {
        return new Refusal(RULE, messageKey, field, null);
    }
}
