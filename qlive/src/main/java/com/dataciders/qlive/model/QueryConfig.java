package com.dataciders.qlive.model;

import com.dataciders.qlive.model.condition.CNode;
import com.dataciders.qlive.runtime.util.FilterDSLDecompiler;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Generalized query configuration
 */
public class QueryConfig
{
    private CNode condition;

    private int offset;

    private int pageSize;

    private List<CNode>  sortFields;

    public CNode getCondition()
    {
        return condition;
    }


    public void setCondition(CNode condition)
    {
        this.condition = condition;
    }


    /**
     * Returns the query offset
     */
    @NotNull
    public int getOffset()
    {
        return offset;
    }


    public void setOffset(int offset)
    {
        this.offset = offset;
    }


    /**
     * Returns the page size / limit
     */
    @NotNull
    public int getPageSize()
    {
        return pageSize;
    }


    public void setPageSize(int pageSize)
    {
        this.pageSize = pageSize;
    }


    /**
     * Field sort expressions
     */
    @NotNull
    public List<CNode> getSortFields()
    {
        return sortFields;
    }


    public void setSortFields(List<CNode> sortFields)
    {
        this.sortFields = sortFields;
    }


    @Override
    public String toString()
    {
        return super.toString() + ": "
            + "condition = " + FilterDSLDecompiler.decompile(condition)
            + ", offset = " + offset
            + ", pageSize = " + pageSize
            + ", sortFields = [" + sortFields.stream()
                   .map(FilterDSLDecompiler::getFilterExpression)
                   .collect(Collectors.joining(", ")) + "]"
            ;
    }
}
