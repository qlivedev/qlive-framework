package io.github.qlivedev.model.merge;

import de.quinscape.domainql.generic.GenericScalar;
import jakarta.validation.constraints.NotNull;

/**
 * One field of one row set to one value.
 *
 * The value travels as a GenericScalar, i.e. as the name of a scalar type plus a value that type's coercing
 * understands. That is what lets one mutation write any type: the value arrives already converted to the
 * Java type the column has, and no application ever declares an input type for it.
 */
public class FieldChange
{
    private String field;

    private GenericScalar value;


    /**
     * Name of the field, as the GraphQL type spells it. Not the column name -- the domain is what says which
     * column that is.
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
     * The value to write, or null to write null. A GenericScalar whose own value is null means the same
     * thing, so a client that has a type name at hand may say it either way.
     */
    public GenericScalar getValue()
    {
        return value;
    }


    public void setValue(GenericScalar value)
    {
        this.value = value;
    }


    @Override
    public String toString()
    {
        return super.toString() + ": "
            + "field = '" + field + '\''
            + ", value = " + value
            ;
    }
}
