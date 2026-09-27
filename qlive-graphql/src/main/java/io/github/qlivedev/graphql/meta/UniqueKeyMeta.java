package io.github.qlivedev.graphql.meta;

import java.util.List;

/**
 * One unique constraint of a table-backed domain type, as the domain meta data describes it to the client.
 *
 * @see UniqueKeyProvider
 */
public final class UniqueKeyMeta
{
    private final String name;

    private final List<String> fields;

    private final boolean primary;

    private final boolean nullable;


    public UniqueKeyMeta(String name, List<String> fields, boolean primary, boolean nullable)
    {
        this.name = name;
        this.fields = List.copyOf(fields);
        this.primary = primary;
        this.nullable = nullable;
    }


    /**
     * Name of the constraint in the database.
     *
     * @return constraint name
     */
    public String getName()
    {
        return name;
    }


    /**
     * Fields of the domain type the constraint covers, in constraint order.
     *
     * @return field names
     */
    public List<String> getFields()
    {
        return fields;
    }


    /**
     * Whether this is the primary key.
     *
     * @return <code>true</code> for the primary key
     */
    public boolean isPrimary()
    {
        return primary;
    }


    /**
     * Whether any of the fields is nullable. The database lets any number of rows hold NULL in a unique constraint, so
     * such a constraint does not make rows distinct -- a sort covering it can still have ties.
     *
     * @return <code>true</code> if at least one field is nullable
     */
    public boolean isNullable()
    {
        return nullable;
    }


    @Override
    public String toString()
    {
        return super.toString() + ": "
            + "name = '" + name + '\''
            + ", fields = " + fields
            + ", primary = " + primary
            + ", nullable = " + nullable
            ;
    }
}
