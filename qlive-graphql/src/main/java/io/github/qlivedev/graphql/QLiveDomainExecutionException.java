package io.github.qlivedev.graphql;

public class QLiveDomainExecutionException
    extends QLiveDomainException
{
    private static final long serialVersionUID = -8343955271035853751L;


    public QLiveDomainExecutionException(String message)
    {
        super(message);
    }


    public QLiveDomainExecutionException(String message, Throwable cause)
    {
        super(message, cause);
    }


    public QLiveDomainExecutionException(Throwable cause)
    {
        super(cause);
    }
}
