package com.dataciders.qlive.model.bootstrap;

public class Injection
{
    private final Object data;

    private final String type;

    private final Object meta;


    public Injection(Object data, String type)
    {
        this(data, type, null);
    }
    
    public Injection(Object data, String type, Object meta)
    {
        this.data = data;
        this.type = type;
        this.meta = meta;
    }


    public Object getData()
    {
        return data;
    }


    public String getType()
    {
        return type;
    }


    public Object getMeta()
    {
        return meta;
    }
}
