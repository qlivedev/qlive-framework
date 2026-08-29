package com.dataciders.qlive.runtime.config;

import de.quinscape.domainql.DomainQL;
import de.quinscape.spring.jsview.JsViewContext;
import de.quinscape.spring.jsview.JsViewProvider;
import jakarta.servlet.ServletContext;
import org.springframework.context.annotation.Lazy;

public class QLiveViewDataProvider
    implements JsViewProvider
{

    private final ServletContext servletContext;

    private final DomainQL domainQL;


    public QLiveViewDataProvider(
        ServletContext servletContext, @Lazy DomainQL domainQL
    )
    {
        this.servletContext = servletContext;
        this.domainQL = domainQL;
    }


    @Override
    public void provide(JsViewContext ctx) throws Exception
    {
        ctx.provideViewData("contextPath", servletContext.getContextPath());
        ctx.provideViewData("meta", domainQL.getMetaData());
    }
}
