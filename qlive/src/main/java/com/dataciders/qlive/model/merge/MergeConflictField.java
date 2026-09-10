package com.dataciders.qlive.model.merge;

import de.quinscape.domainql.generic.GenericScalar;
import jakarta.validation.constraints.NotNull;

/**
 * One field of a row that could not be written as asked.
 *
 * The vocabulary is deliberately not "ours" and "theirs". Whoever wrote first is gone; the only person still
 * here is the one whose save just bounced, and what they are choosing between is the value they typed and the
 * value that is in the database. So: mine and stored.
 */
public class MergeConflictField
{
    private String field;

    private GenericScalar mine;

    private GenericScalar stored;


    /**
     * Name of the field, as the GraphQL type spells it.
     */
    @NotNull
    public String getField()
    {
        return field;
    }


    public void setField(String field)
    {
        this.field = field;
    }


    /**
     * The value the user meant to write, echoed back. Null where the conflict carries no values, i.e. where
     * either the type or the caller did not ask to resolve conflicts.
     */
    public GenericScalar getMine()
    {
        return mine;
    }


    public void setMine(GenericScalar mine)
    {
        this.mine = mine;
    }


    /**
     * The value that is in the database. Null where the conflict carries no values, and null as a value in
     * its own right where the stored value is null -- the GenericScalar is there either way when values are
     * carried at all.
     */
    public GenericScalar getStored()
    {
        return stored;
    }


    public void setStored(GenericScalar stored)
    {
        this.stored = stored;
    }


    @Override
    public String toString()
    {
        return super.toString() + ": "
            + "field = '" + field + '\''
            + ", mine = " + mine
            + ", stored = " + stored
            ;
    }
}
