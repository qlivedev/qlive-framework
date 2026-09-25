package io.github.qlivedev.graphql;

public class QLiveDomainBuilderException
    extends RuntimeException
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


    public QLiveDomainBuilderException(
        String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace
    )
    {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
