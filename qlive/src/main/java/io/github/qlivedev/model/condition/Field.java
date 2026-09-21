package io.github.qlivedev.model.condition;

import jakarta.validation.constraints.NotNull;

public class Field
    extends ValueNode
{
    private String name;
    public Field()
    {
        this(null);
    }
    
    public Field(String name)
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
