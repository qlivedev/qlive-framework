package com.dataciders.qlive.model.condition;

import jakarta.validation.constraints.NotNull;

public class Component
    extends CNode
{
    private String id;

    private CNode condition;


    @NotNull
    public String getId()
    {
        return id;
    }


    public void setId(String id)
    {
        this.id = id;
    }


    @NotNull
    public CNode getCondition()
    {
        return condition;
    }


    public void setCondition(CNode condition)
    {
        this.condition = condition;
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
            + "id = '" + id + '\''
            + ", condition = " + condition
            ;
    }
}
