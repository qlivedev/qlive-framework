package io.github.qlivedev.model.bootstrap;

/// Encapsulates the data and meta data for a data injection.
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


    /// The payload for the injection
    public Object getData()
    {
        return data;
    }


    /// The GraphQL type of the injection
    public String getType()
    {
        return type;
    }


    /// Arbitrary meta data
    public Object getMeta()
    {
        return meta;
    }
}
