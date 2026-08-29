package com.dataciders.qlive.model.condition;

import jakarta.validation.constraints.NotNull;

/**
 * Abstract base class for all types in the FilterDSL condition hierarchy.
 */
public abstract class CNode
{
    @NotNull 
    public String getType()
    {
        return this.getClass().getSimpleName();
    }


    /**
     * Subclasses must implement the method to call their own overloading of the
     * visitor's visit() method.
     *
     * @param visitor   visitor
     */
    public abstract <D> Object accept(ConditionVisitor<D> visitor, D data);
}
