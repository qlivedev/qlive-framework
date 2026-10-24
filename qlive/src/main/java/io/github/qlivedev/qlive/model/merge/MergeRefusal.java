package io.github.qlivedev.qlive.model.merge;

import jakarta.validation.constraints.NotNull;

/**
 * One invariant one row broke, and what to tell the user.
 *
 * Like a conflict, a refusal is data: the merge rolled back, the user still has everything they typed, and the
 * form shows the message at the field the rule blames.
 */
public class MergeRefusal
{
    private String type;

    private String id;

    private String field;

    private String messageKey;

    private String errorClass;


    /**
     * GraphQL name of the type whose row this is.
     */
    @NotNull
    public String getType()
    {
        return type;
    }


    public void setType(String type)
    {
        this.type = type;
    }


    /**
     * Id of the row.
     */
    @NotNull
    public String getId()
    {
        return id;
    }


    public void setId(String id)
    {
        this.id = id;
    }


    /**
     * The field the rule blames, as the GraphQL type spells it, or null where it blames none.
     */
    public String getField()
    {
        return field;
    }


    public void setField(String field)
    {
        this.field = field;
    }


    /**
     * i18n key of the message.
     */
    @NotNull
    public String getMessageKey()
    {
        return messageKey;
    }


    public void setMessageKey(String messageKey)
    {
        this.messageKey = messageKey;
    }


    /**
     * Class a layout can react to, or null where the rule names none.
     */
    public String getErrorClass()
    {
        return errorClass;
    }


    public void setErrorClass(String errorClass)
    {
        this.errorClass = errorClass;
    }


    @Override
    public String toString()
    {
        return super.toString() + ": "
            + "type = '" + type + '\''
            + ", id = '" + id + '\''
            + ", field = '" + field + '\''
            + ", messageKey = '" + messageKey + '\''
            + ", errorClass = '" + errorClass + '\''
            ;
    }
}
