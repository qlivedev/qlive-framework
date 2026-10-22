package io.github.qlivedev.qlive.model.condition;

import jakarta.validation.constraints.NotNull;

public class FieldReference
    extends ValueNode
{
    private String name;
    public FieldReference()
    {
        this(null);
    }
    
    public FieldReference(String name)
    {
        this.name = name;
    }

    @NotNull
    public String getName()
    {
        return name;
    }


    public void setName(String name)
    {
        this.name = name;
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
            + "name = '" + name + '\''
            ;
    }
}
