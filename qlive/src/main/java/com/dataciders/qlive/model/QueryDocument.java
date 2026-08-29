package com.dataciders.qlive.model;

import jakarta.validation.constraints.NotNull;
import org.svenson.JSONProperty;

import java.util.List;

/**
 * Container for [T] queries
 */
public class QueryDocument<T>
{
    private final Class<T> cls;

    private QueryConfig config;
    private List<T> rows;
    private int rowCount;


    public QueryDocument(Class<T> cls)
    {
        this.cls = cls;
    }

    /**
     * query config for this document
     */
    @NotNull
    public QueryConfig getConfig()
    {
        return config;
    }


    /**
     * Runtime payload type (always '[T]')
     */
    @JSONProperty("type")
    @NotNull
    public String getType()
    {
        return cls.getSimpleName();
    }

    public void setConfig(QueryConfig config)
    {
        this.config = config;
    }


    /**
     * List of [T] objects
     */
    @NotNull
    public List<T> getRows()
    {
        return rows;
    }


    public void setRows(List<T> rows)
    {
        this.rows = rows;
    }


    public int getRowCount()
    {
        return rowCount;
    }


    public void setRowCount(int rowCount)
    {
        this.rowCount = rowCount;
    }
}
