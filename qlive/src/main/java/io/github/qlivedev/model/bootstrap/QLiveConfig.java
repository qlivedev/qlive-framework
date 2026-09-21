package io.github.qlivedev.model.bootstrap;

import de.quinscape.domainql.meta.DomainQLMeta;

import java.util.Map;

/// Contains the system configuration of a QLive system.
public class QLiveConfig
{
    private String contextPath;

    private Map<String, Object> schema;

    private DomainQLMeta meta;


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


    public DomainQLMeta getMeta()
    {
        return meta;
    }


    public void setMeta(DomainQLMeta meta)
    {
        this.meta = meta;
    }

}
