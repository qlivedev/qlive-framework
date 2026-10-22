package io.github.qlivedev.qlivetest.runtime.config;

import io.github.qlivedev.qlive.graphql.QLiveDomain;
import io.github.qlivedev.qlive.runtime.query.QueryDocumentService;
import io.github.qlivedev.qlive.runtime.service.BootstrapService;
import io.github.qlivedev.qlive.runtime.controller.RelatedRowsController;
import io.github.qlivedev.qlive.runtime.controller.ViteIndexController;
import io.github.qlivedev.qlive.runtime.view.VitePageRenderer;
import io.github.qlivedev.qlivetest.runtime.controller.LoginController;
import graphql.GraphQL;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

@Configuration
public class WebConfiguration
    implements WebMvcConfigurer
{
    private final BootstrapService bootstrapService;


    public WebConfiguration(BootstrapService bootstrapService)
    {
        this.bootstrapService = bootstrapService;
    }


    /**
     * Renders Vite's entry points. Shared with the application's own -- see
     * {@link io.github.qlivedev.qlivetest.runtime.controller.LoginController} -- rather than created per
     * controller, so one cache holds all of them.
     */
    @Bean
    public VitePageRenderer vitePageRenderer()
    {
        return new VitePageRenderer(bootstrapService);
    }


    @Bean
    public ViteIndexController viteIndexController(VitePageRenderer vitePageRenderer)
    {
        return new ViteIndexController(bootstrapService, vitePageRenderer);
    }


    /**
     * Serves the rows the pickers of forms offer.
     */
    @Bean
    public RelatedRowsController relatedRowsController(
        QLiveDomain domain,
        QueryDocumentService queryDocumentService,
        GraphQL graphQL
    )
    {
        return new RelatedRowsController(domain, queryDocumentService, graphQL);
    }


    /**
     * Serves the login page. Declared here rather than found by a component scan, and next to the
     * {@link VitePageRenderer} it renders with.
     */
    @Bean
    public LoginController loginController(VitePageRenderer vitePageRenderer)
    {
        return new LoginController(vitePageRenderer);
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
