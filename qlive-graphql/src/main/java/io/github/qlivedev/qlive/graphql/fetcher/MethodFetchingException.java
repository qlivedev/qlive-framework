package io.github.qlivedev.qlive.graphql.fetcher;

import io.github.qlivedev.qlive.api.QLiveDomainException;

public class MethodFetchingException
    extends QLiveDomainException
{
    private static final long serialVersionUID = -8654277625360267910L;


    public MethodFetchingException(String message)
    {
        super(message);
    }


    public MethodFetchingException(String message, Throwable cause)
    {
        super(message, cause);
    }


    public MethodFetchingException(Throwable cause)
    {
        super(cause);
    }
}
