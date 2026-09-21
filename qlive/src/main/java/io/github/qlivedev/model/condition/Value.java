package io.github.qlivedev.model.condition;

import jakarta.validation.constraints.NotNull;

public class Value
    extends ValueNode
{
    private String scalarType;
    private Object value;

    public Value()
    {
        this(null, null);
    }
    
    public Value(String scalarType, Object value)
    {
        this.scalarType = scalarType;
        this.value = value;
    }


    @NotNull
    public String getScalarType()
    {
        return scalarType;
    }


    public void setScalarType(String scalarType)
    {
        this.scalarType = scalarType;
    }


    public Object getValue()
    {
        return value;
    }


    public void setValue(Object value)
    {
        this.value = value;
    }


    @Override
    public <D> Object accept(ConditionVisitor<D> visitor, D data)
    {
        visitor.visit(this,  data);
        return null;
    }


    @Override
    public String toString()
    {
        return super.toString() + ": "
            + "scalarType = '" + scalarType + '\''
            + ", value = " + value
            ;
    }
}
