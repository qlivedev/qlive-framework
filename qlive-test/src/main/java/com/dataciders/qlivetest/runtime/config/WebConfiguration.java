package com.dataciders.qlivetest.runtime.config;

import com.dataciders.qlive.runtime.service.BootstrapService;
import com.dataciders.qlive.runtime.controller.ViteIndexController;
import com.dataciders.qlive.runtime.view.VitePageRenderer;
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
     * {@link com.dataciders.qlivetest.runtime.controller.LoginController} -- rather than created per
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


    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry)
    {
        registry
            .addResourceHandler("/static/**")
            .addResourceLocations("/static/")
            .setCacheControl(CacheControl.maxAge(90, TimeUnit.DAYS));
    }

}
