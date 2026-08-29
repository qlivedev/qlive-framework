package com.dataciders.qlive.runtime.util;

import de.quinscape.spring.jsview.loader.JSONResourceConverter;
import de.quinscape.spring.jsview.loader.ResourceConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.function.Function;

/**
 * <p>
 *  Wrapping resource converter that accepts a consumer that is called whenever the resource changes.
 * </p>
 * <p>
 * By default, this happens lazily, i.e., the resource is converted when it is requested next. In most use-cases it seems
 * to make sense to call {@link de.quinscape.spring.jsview.loader.FileResourceHandle#setEager(boolean)} to set the file
 * resource handle to eager so that this listening converter is notified right away.
 * </p>
 *
 * @param <T> content type
 */
public class ResourceConverterInterceptor<T>
    implements ResourceConverter<T>
{
    private final JSONResourceConverter<T> orig;

    private final Function<T, T> onContentChange;

    private final static Logger log = LoggerFactory.getLogger(ResourceConverterInterceptor.class);

    /**
     * Creates a new ResourceConverterInterceptor
     *
     * @param orig              Resource Converter doing the initial conversion
     * @param onContentChange   callback to intercept the newly converted value. Must return the final value.
     */
    public ResourceConverterInterceptor(JSONResourceConverter<T> orig, Function<T,T> onContentChange)
    {

        log.debug("Creating ResourceConverterInterceptor for {}", orig);

        this.orig = orig;
        this.onContentChange = onContentChange;
    }


    /**
     * Called when a file resource change is detected and the new content is converted to a Java object.
     *
     * @param inputStream      input stream
     *
     * @return
     */
    @Override
    public T readStream(InputStream inputStream)
    {
        final T result = orig.readStream(inputStream);
        log.debug("New content is: {}", result);
        return onContentChange.apply(result);
    }


    @Override
    public byte[] toByteArray(T value)
    {
        // Is only used when a Resource handle is updated with new content from the Java side.
        // so we just delegate to the original resource converter
        return orig.toByteArray(value);
    }


}
