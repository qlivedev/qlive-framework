package com.dataciders.qlive.model.condition;

import jakarta.validation.constraints.NotNull;
import org.svenson.JSONProperty;

/**
 * Abstract base class for all types in the FilterDSL condition hierarchy.
 */
public abstract class CNode
{
    /// The discriminator the FilterDSL JSON carries, derived from the class name. It is read-only in both
    /// directions of the word: there is no field behind it, and a parser must not try to set it. It is
    /// still generated, because it is what tells a reader which node it is looking at -- see
    /// ConditionParser, which selects the subclass by it.
    @JSONProperty(readOnly = true)
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
