package com.dataciders.qlive.model.merge;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * One row the merge could not write, and why.
 *
 * A conflict is data. The framework ships no dialog and nothing here blocks: the merge rolled back, the user
 * still has everything they typed, and the form they were editing is where they decide what to do about it.
 */
public class MergeConflict
{
    private String type;

    private String id;

    private String version;

    private boolean deleted;

    private List<MergeConflictField> fields;


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
     * The version standing in the database now, and the base a second attempt has to be made against. Null
     * where the row is gone, and null for a type that carries no version field.
     */
    public String getVersion()
    {
        return version;
    }


    public void setVersion(String version)
    {
        this.version = version;
    }


    /**
     * true if the row is not there at all -- removed by somebody else, or never created. There is nothing to
     * merge into and nothing to choose between, so no fields come with it.
     */
    @NotNull
    public boolean isDeleted()
    {
        return deleted;
    }


    public void setDeleted(boolean deleted)
    {
        this.deleted = deleted;
    }


    /**
     * The fields that clashed. Empty for a deletion, which touches no fields, and empty where the row is
     * gone.
     */
    @NotNull
    public List<MergeConflictField> getFields()
    {
        return fields;
    }


    public void setFields(List<MergeConflictField> fields)
    {
        this.fields = fields;
    }


    @Override
    public String toString()
    {
        return super.toString() + ": "
            + "type = '" + type + '\''
            + ", id = '" + id + '\''
            + ", version = '" + version + '\''
            + ", deleted = " + deleted
            + ", fields = " + fields
            ;
    }
}
