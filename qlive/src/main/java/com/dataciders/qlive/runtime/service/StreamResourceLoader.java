package com.dataciders.qlive.runtime.service;

import de.quinscape.spring.jsview.loader.ResourceConverter;
import de.quinscape.spring.jsview.loader.ResourceHandle;
import de.quinscape.spring.jsview.loader.ResourceLoader;
import de.quinscape.spring.jsview.loader.StreamResourceHandle;

import jakarta.servlet.ServletContext;

/**
 * Non-reloadable {@link ResourceLoader} that always serves {@link StreamResourceHandle}s straight from the
 * servlet context, regardless of whether {@link ServletContext#getRealPath(String)} resolves to a filesystem
 * path.
 * <p>
 *     {@link de.quinscape.spring.jsview.loader.ServletResourceLoader} switches into file-watching hot-reload mode
 *     whenever {@code getRealPath()} is non-null, which is meant for exploded-WAR development setups where the
 *     resources on disk are actually the ones being served and edited. Under embedded Tomcat that path is no
 *     longer meaningful: Spring Boot always provisions a scratch {@code tomcat-docbase} temp directory even
 *     though nothing reads from or writes to it, which would otherwise trigger a pointless resource watcher.
 * </p>
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
