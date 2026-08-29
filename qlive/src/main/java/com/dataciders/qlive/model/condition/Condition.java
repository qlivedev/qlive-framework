package com.dataciders.qlive.model.condition;

import jakarta.validation.constraints.NotNull;
import org.svenson.JSONTypeHint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Condition
    extends CNode
    implements FunctionNode
{
    private String name;

    private List<CNode> operands;


    @Override
    @NotNull
    public String getName()
    {
        return name;
    }


    @Override
    public void setName(String name)
    {
        this.name = name;
    }


    @Override
    @NotNull
    @JSONTypeHint(CNode.class)
    public List<CNode> getOperands()
    {
        return operands;
    }


    @Override
    public void setOperands(List<CNode> operands)
    {
        this.operands = operands;
    }


    @Override
    public <D> Object accept(ConditionVisitor<D> visitor, D data)
    {
        visitor.visit(this,  data);
        return null;
    }

    /// Intermal method to create a name condition node.
    ///
    /// @param name         name of the operation
    /// @param operands     additional operands of the operation besides <code>this</code>
    ///
    /// @return operation node
    static Condition create(String name, CNode... operands)
    {
        final Condition condition = new Condition();
        condition.setName(name);
        final ArrayList<CNode> allOperands = new ArrayList<>();
        Collections.addAll(allOperands, operands);
        condition.setOperands(allOperands);
        return condition;
    }

    public Condition not() { return create("not", this); }
    public Condition or(CNode a) { return create("or", this, a); }
    public Condition orNot(CNode a) { return create("orNot", this, a); }
    public Condition and(CNode a) { return create("and", this, a); }
    public Condition andNot(CNode a) { return create("andNot", this, a); }


    @Override
    public String toString()
    {
        return super.toString() + ": "
            + "name = '" + name + '\''
            + ", operands = " + operands
            ;
    }
}
