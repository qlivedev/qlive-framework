package com.dataciders.qlive.runtime.service;

import com.dataciders.qlive.model.bootstrap.QLiveConfig;
import de.quinscape.domainql.DomainQL;
import jakarta.servlet.ServletContext;
import org.springframework.context.annotation.Lazy;

public class QLiveConfigService
{
    private final ServletContext servletContext;

    private final DomainQL domainQL;


    public QLiveConfigService(
        ServletContext servletContext, @Lazy DomainQL domainQL
    )
    {
        this.servletContext = servletContext;
        this.domainQL = domainQL;
    }


    public QLiveConfig provideConfig()
    {
        QLiveConfig config = new QLiveConfig();
        config.setContextPath(servletContext.getContextPath());
        config.setMeta(domainQL.getMetaData());
        return config;
    }
}
