package com.dataciders.qlive.runtime;

public class QLiveException
    extends RuntimeException
{
    private static final long serialVersionUID = -2610478704903193053L;


    public QLiveException(String message)
    {
        super(message);
    }


    public QLiveException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public QLiveException(Throwable cause)
    {
        super(cause);
    }
}
