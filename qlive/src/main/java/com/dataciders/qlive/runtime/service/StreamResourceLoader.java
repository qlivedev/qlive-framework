package com.dataciders.qlive.runtime.service;

import de.quinscape.spring.jsview.loader.ResourceConverter;
import de.quinscape.spring.jsview.loader.ResourceHandle;
import de.quinscape.spring.jsview.loader.ResourceLoader;
import de.quinscape.spring.jsview.loader.StreamResourceHandle;

import jakarta.servlet.ServletContext;

/**
 * Non-reloadable {@link ResourceLoader} that always serves {@link StreamResourceHandle}s from the
 * servlet context.
 *
 * We don't rely on any hot-reloading of resources for the application. The vite dev server pushes
 * back the changes.
 */
public class StreamResourceLoader implements ResourceLoader
{
    private final ServletContext servletContext;

    private final String resourcePath;

    public StreamResourceLoader(ServletContext servletContext, String resourcePath)
    {
        this.servletContext = servletContext;
        this.resourcePath = resourcePath;
    }


    @Override
    public <T> ResourceHandle<T> getResourceHandle(String path, ResourceConverter<T> converter)
    {
        if (path == null)
        {
            throw new IllegalArgumentException("path can't be null");
        }

        if (converter == null)
        {
            throw new IllegalArgumentException("loader can't be null");
        }

        return new StreamResourceHandle<>(servletContext, resourcePath + ensureLeadingSlash(path), converter);
    }


    private String ensureLeadingSlash(String path)
    {
        if (path.startsWith("/"))
        {
            return path;
        }
        return "/" + path;
    }


    @Override
    public void shutDown()
    {
        // nothing to do, StreamResourceHandle holds no external resources
    }
}
