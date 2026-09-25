package io.github.qlivedev.graphql.generic;

import io.github.qlivedev.graphql.QLiveDomainException;

public class DomainObjectCreationException
    extends QLiveDomainException
{
    private static final long serialVersionUID = -494757270609475551L;


    public DomainObjectCreationException(String message)
    {
        super(message);
    }


    public DomainObjectCreationException(String message, Throwable cause)
    {
        super(message, cause);
    }


    public DomainObjectCreationException(Throwable cause)
    {
        super(cause);
    }
}
