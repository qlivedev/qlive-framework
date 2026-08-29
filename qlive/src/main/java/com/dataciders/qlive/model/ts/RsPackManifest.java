package com.dataciders.qlive.model.ts;

import de.quinscape.spring.jsview.util.JSONUtil;
import org.svenson.DynamicProperties;
import org.svenson.JSONParameters;

import java.util.Map;
import java.util.Set;

/**
 * POJO implementation for rspack/rspack-manifest-plugin's manifest. Accepts any key, ensures the values are strings.
 */
public class RsPackManifest
    implements DynamicProperties
{
    private final Map<String,Object> map;

    public RsPackManifest(
        @JSONParameters
        Map<String,Object> manifest
    )
    {
        validate(manifest);
        this.map =  manifest;
    }

    public Object getProperty(String name)
    {
        return map.get(name);
    }

    public void setProperty(String name, Object value)
    {
        if (!(value instanceof String))
        {
            throw new IllegalArgumentException("Value must be of type String");
        }
        map.put(name, value);
    }

    public Set<String> propertyNames()
    {
        return map.keySet();
    }

    public boolean hasProperty(String name)
    {
        return map.containsKey(name);
    }

    public Object removeProperty(String name)
    {
        return map.remove(name);
    }


    private void validate(Map<String, Object> map)
    {
        for (Map.Entry<String, Object> e : map.entrySet())
        {
            if (!(e.getValue() instanceof String))
            {
                throw new IllegalArgumentException("Value for property " + e.getKey() + " is not a string: " + e.getValue());
            }
        }
    }


    @Override
    public String toString()
    {
        return super.toString() + ": "
            + "map = " + map
            ;
    }
}
