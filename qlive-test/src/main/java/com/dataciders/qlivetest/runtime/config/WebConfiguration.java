package com.dataciders.qlivetest.runtime.config;

import com.dataciders.qlive.runtime.service.BootstrapService;
import com.dataciders.qlive.runtime.controller.ViteIndexController;
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


    @Bean
    public ViteIndexController viteIndexController()
    {
        return new ViteIndexController(bootstrapService);
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
