package io.github.qlivedev.qlive.graphql;

import io.github.qlivedev.qlive.api.QLiveDomainException;

public class InvalidMachineStateException
    extends QLiveDomainException
{
    private static final long serialVersionUID = 7078932465087727423L;


    public InvalidMachineStateException(String message)
    {
        super(message);
    }


    public InvalidMachineStateException(String message, Throwable cause)
    {
        super(message, cause);
    }


    public InvalidMachineStateException(Throwable cause)
    {
        super(cause);
    }
}
