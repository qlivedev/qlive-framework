package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveMutation;
import io.github.qlivedev.qlive.api.generic.DomainObject;
import io.github.qlivedev.qlive.api.json.JSONUtil;

import java.util.Map;
import java.util.TreeMap;

@QLiveLogic
public class GenericDomainLogic
{
    @QLiveMutation
    public String store(DomainObject domainObject)
    {

        // sort properties alphabetically
        Map<String,String> map = new TreeMap<>();

        map.put("_javaType", domainObject.getClass().getName());
        for (String name : domainObject.propertyNames())
        {
            final Object value = domainObject.getProperty(name);
            map.put(name, "=" + value + ( value != null ? " (" + value.getClass().getSimpleName() + ")" : ""));
        }
        return JSONUtil.DEFAULT_GENERATOR.forValue(map);
    }

    @QLiveMutation
    public DomainObject genericDomainObjectAsNull(DomainObject domainObject)
    {

        if (domainObject != null)
        {
            throw new IllegalArgumentException("domainObject must be null");
        }
        return null;
    }
}
