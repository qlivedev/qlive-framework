package io.github.qlivedev.model.bootstrap;

import io.github.qlivedev.graphql.meta.DomainMeta;

import java.util.List;
import java.util.Map;

/// Contains the system configuration of a QLive system.
public class QLiveConfig
{
    private String contextPath;

    private Map<String, Object> schema;

    private DomainMeta meta;

    private Map<String, List<Map<String, Object>>> catalogs;


    public String getContextPath()
    {
        return contextPath;
    }


    public void setContextPath(String contextPath)
    {
        this.contextPath = contextPath;
    }


    public void setSchema(Map<String, Object> schema)
    {
        this.schema = schema;
    }


    public Map<String, Object> getSchema()
    {
        return schema;
    }


    public DomainMeta getMeta()
    {
        return meta;
    }


    public void setMeta(DomainMeta meta)
    {
        this.meta = meta;
    }


    /// Every row of every catalog type, by type name, see `CatalogMeta`.
    public Map<String, List<Map<String, Object>>> getCatalogs()
    {
        return catalogs;
    }


    public void setCatalogs(Map<String, List<Map<String, Object>>> catalogs)
    {
        this.catalogs = catalogs;
    }
}
