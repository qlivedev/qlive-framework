package com.dataciders.qlive.model.push;

import java.util.List;

/// A channel payload, of the kind an application writes: an ordinary bean with ordinary properties, no
/// DomainQL registration and no relation machinery anywhere in sight.
public class TestPayload
{
    private String name;

    private Long count;

    private List<String> tags;


    public String getName()
    {
        return name;
    }


    public void setName(String name)
    {
        this.name = name;
    }


    public Long getCount()
    {
        return count;
    }


    public void setCount(Long count)
    {
        this.count = count;
    }


    public List<String> getTags()
    {
        return tags;
    }


    public void setTags(List<String> tags)
    {
        this.tags = tags;
    }
}
