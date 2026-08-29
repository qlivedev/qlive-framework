package com.dataciders.qlive.model.bootstrap;

import de.quinscape.domainql.meta.DomainQLMeta;

/**
 * Data embedded into the served page (or fetched live in dev) so the frontend can boot
 * with the same data regardless of how it got there.
 */
public class QLiveConfig
{
    private String contextPath;

    private DomainQLMeta meta;


    public String getContextPath()
    {
        return contextPath;
    }


    public void setContextPath(String contextPath)
    {
        this.contextPath = contextPath;
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
