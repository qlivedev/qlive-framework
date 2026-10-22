package io.github.qlivedev.qlive.graphql.logic;

import io.github.qlivedev.qlive.api.QLiveDomainException;

public class InputObjectConversionException
    extends QLiveDomainException
{

    private static final long serialVersionUID = -5509707385416798594L;


    public InputObjectConversionException(String message)
    {
        super(message);
    }


    public InputObjectConversionException(String message, Throwable cause)
    {
        super(message, cause);
    }


    public InputObjectConversionException(Throwable cause)
    {
        super(cause);
    }
}
