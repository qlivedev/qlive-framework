package io.github.qlivedev.qlive.graphql;

import io.github.qlivedev.qlive.api.QLiveDomainException;

public class QLiveDomainTypeException
    extends QLiveDomainException
{
    private static final long serialVersionUID = -4246712981720721765L;


    public QLiveDomainTypeException(String message)
    {
        super(message);
    }


    public QLiveDomainTypeException(String message, Throwable cause)
    {
        super(message, cause);
    }


    public QLiveDomainTypeException(Throwable cause)
    {
        super(cause);
    }
}
