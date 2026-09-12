package com.dataciders.qlive.model.merge;

import jakarta.validation.constraints.NotNull;

/**
 * One row to remove.
 *
 * The same optimistic lock as a change and for the same reason: a row that changed since it was read may
 * not be the row the user meant to delete, and a delete that quietly removed it anyway would be the
 * worst-behaved lost update of the lot.
 */
public class EntityDeletion
{
    private String type;

    private String id;

    private String version;


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
     * Id of the row to remove.
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
     * The version the row was read at. Null for a type that carries no version field, where the delete runs
     * unconditionally.
     */
    public String getVersion()
    {
        return version;
    }


    public void setVersion(String version)
    {
        this.version = version;
    }


    @Override
    public String toString()
    {
        return super.toString() + ": "
            + "type = '" + type + '\''
            + ", id = '" + id + '\''
            + ", version = '" + version + '\''
            ;
    }
}
