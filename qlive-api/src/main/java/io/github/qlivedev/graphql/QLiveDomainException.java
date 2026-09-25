package io.github.qlivedev.graphql;

/**
 * Generic runtime exception for the assembly of a domain and for the schema it produced.
 */
public class QLiveDomainException
    extends RuntimeException
{
    private static final long serialVersionUID = -3518149341721548644L;


    public QLiveDomainException(String message)
    {
        super(message);
    }


    public QLiveDomainException(String message, Throwable cause)
    {
        super(message, cause);
    }


    public QLiveDomainException(Throwable cause)
    {
        super(cause);
    }
}
