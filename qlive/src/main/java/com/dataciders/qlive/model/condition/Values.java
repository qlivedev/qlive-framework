package com.dataciders.qlive.model.condition;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public class Values
    extends CNode
{
    private String scalarType;

    private List<Object> values;


    @NotNull
    public String getScalarType()
    {
        return scalarType;
    }


    public void setScalarType(String scalarType)
    {
        this.scalarType = scalarType;
    }


    @NotNull
    public List<Object> getValues()
    {
        return values;
    }

    
    public void setValues(List<Object> values)
    {
        this.values = values;
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
            + ", value = " + values
            ;
    }
}
