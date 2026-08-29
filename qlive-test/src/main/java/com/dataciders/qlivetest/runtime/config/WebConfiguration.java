package com.dataciders.qlivetest.runtime.config;

import de.quinscape.domainql.DomainQL;
import com.dataciders.qlive.runtime.config.QLiveViewDataProvider;
import com.dataciders.qlive.runtime.util.RsPackAssetProvider;
import de.quinscape.spring.jsview.JsViewResolver;
import de.quinscape.spring.jsview.loader.ResourceLoader;
import jakarta.servlet.ServletContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewResolverRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

@Configuration
public class WebConfiguration
    implements WebMvcConfigurer
{
    private final ServletContext servletContext;

    private final ResourceLoader resourceLoader;

    private final QLiveViewDataProvider qLiveViewDataProvider;

    private final DomainQL domainQL;


    public WebConfiguration(
        ServletContext servletContext,
        ResourceLoader resourceLoader,
        QLiveViewDataProvider qLiveViewDataProvider,
        DomainQL domainQL
    )
    {
        this.servletContext = servletContext;
        this.resourceLoader = resourceLoader;
        this.qLiveViewDataProvider = qLiveViewDataProvider;
        this.domainQL = domainQL;
    }


    @Bean
    public JsViewController jsViewController()
    {
        return new JsViewController();
    }


    @Override
    public void configureViewResolvers(ViewResolverRegistry registry)
    {
        registry.viewResolver(
            JsViewResolver.newResolver(servletContext, "WEB-INF/template.html")
                .withResourceLoader(resourceLoader)
                .withAssetProvider(
                    new RsPackAssetProvider(
                        resourceLoader,
                        "/static/manifest.json",
                        "/static/")
                )
                .withViewDataProvider(
                    qLiveViewDataProvider
                )
                .withViewDataProvider(
                    ctx -> {
                        ctx.setPlaceholderValue("TITLE", "QLive Test: " + ctx.getJsView().getEntryPoint());
                    }
                )
                .build()
        );
    }


    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry)
    {
        registry
            .addResourceHandler("/static/**")
            .addResourceLocations("/static/")
            .setCacheControl(CacheControl.maxAge(90, TimeUnit.DAYS));
    }

}
