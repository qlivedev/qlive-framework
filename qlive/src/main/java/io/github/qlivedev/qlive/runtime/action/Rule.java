package io.github.qlivedev.qlive.runtime.action;

import io.github.qlivedev.qlive.model.condition.Condition;

/// A rule of an action: a condition that has to hold for the action to run, and what to tell the user where it
/// does not.
///
/// The condition's field paths start with a parameter name of the action method: `bar.description` reads a
/// subject's stored field, `comment` an input value. A rule reading stored fields of one subject is evaluated
/// against the database, once for all its rows; every other reference is bound as a value first. A rule reading
/// no stored field is evaluated in memory.
///
/// A rule holds where its condition is true. Where it is false or unknown -- a comparison with a null column in
/// SQL -- the action is refused.
public final class Rule
{
    private final Condition condition;

    private final String field;

    private final String messageKey;

    private final String errorClass;


    /// @param condition    condition that has to hold
    /// @param field        path of the field the rule blames, or `null`
    /// @param messageKey   i18n key of the message
    public Rule(Condition condition, String field, String messageKey)
    {
        this(condition, field, messageKey, null);
    }


    /// @param condition    condition that has to hold
    /// @param field        path of the field the rule blames, or `null`
    /// @param messageKey   i18n key of the message
    /// @param errorClass   class a layout can react to, or `null`
    public Rule(Condition condition, String field, String messageKey, String errorClass)
    {
        if (condition == null)
        {
            throw new IllegalArgumentException("condition can't be null");
        }
        if (messageKey == null)
        {
            throw new IllegalArgumentException("messageKey can't be null");
        }

        this.condition = condition;
        this.field = field;
        this.messageKey = messageKey;
        this.errorClass = errorClass;
    }


    public Condition getCondition()
    {
        return condition;
    }


    public String getField()
    {
        return field;
    }


    public String getMessageKey()
    {
        return messageKey;
    }


    public String getErrorClass()
    {
        return errorClass;
    }


    @Override
    public String toString()
    {
        return "Rule(" + messageKey + (field != null ? ", " + field : "") + ")";
    }
}
