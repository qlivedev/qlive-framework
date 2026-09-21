package io.github.qlivedev.model.condition;

import java.util.List;

/**
 * Implemented by operation and condition nodes which are all just defined by their name and their operands.
 */
public interface FunctionNode
{
    String getName();

    void setName(String name);

    List<CNode> getOperands();

    void setOperands(List<CNode> operands);

}
