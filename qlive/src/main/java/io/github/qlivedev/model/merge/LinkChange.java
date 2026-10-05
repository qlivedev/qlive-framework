package io.github.qlivedev.model.merge;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * The associations one row gained and lost through one many-to-many field.
 *
 * Named by the row and the field, e.g. Bar "bar-1" and "bazes", and by the ids of the rows on the other end.
 * The merge turns each into an insert or a delete of a link row keyed by the pair, so neither carries the link
 * row's id or version: an association is the pair, and nothing about it can conflict. Adding one that exists
 * and removing one that is gone both leave the database as asked and are no error.
 */
public class LinkChange
{
    private String type;

    private String id;

    private String field;

    private List<String> added;

    private List<String> removed;


    /**
     * GraphQL name of the type the field is on.
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
     * Id of the row whose associations these are.
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
     * Name of the many-to-many field, e.g. "bazes".
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
     * Ids of the rows on the other end the row is now associated with and was not.
     */
    @NotNull
    public List<String> getAdded()
    {
        return added;
    }


    public void setAdded(List<String> added)
    {
        this.added = added;
    }


    /**
     * Ids of the rows on the other end the row was associated with and is no longer.
     */
    @NotNull
    public List<String> getRemoved()
    {
        return removed;
    }


    public void setRemoved(List<String> removed)
    {
        this.removed = removed;
    }


    @Override
    public String toString()
    {
        return super.toString() + ": "
            + "type = '" + type + '\''
            + ", id = '" + id + '\''
            + ", field = '" + field + '\''
            + ", added = " + added
            + ", removed = " + removed
            ;
    }
}
