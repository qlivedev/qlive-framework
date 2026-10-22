package io.github.qlivedev.qlive.graphql;

import io.github.qlivedev.qlive.api.QLiveDomainException;

/**
 * Thrown when a domain cannot be configured: the builder was told something that contradicts what it was told
 * before, or names something that is not there.
 */
public class QLiveDomainBuilderException
    extends QLiveDomainException
{
    private static final long serialVersionUID = -6107054351550261974L;


    public QLiveDomainBuilderException(String message)
    {
        super(message);
    }


    public QLiveDomainBuilderException(String message, Throwable cause)
    {
        super(message, cause);
    }


    public QLiveDomainBuilderException(Throwable cause)
    {
        super(cause);
    }
}
