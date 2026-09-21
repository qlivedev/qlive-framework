package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLMutation;
import io.github.qlivedev.graphql.generic.DomainObject;
import io.github.qlivedev.util.JSONUtil;

import java.util.Map;
import java.util.TreeMap;

@GraphQLLogic
public class GenericDomainLogic
{
    @GraphQLMutation
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

    @GraphQLMutation
    public DomainObject genericDomainObjectAsNull(DomainObject domainObject)
    {

        if (domainObject != null)
        {
            throw new IllegalArgumentException("domainObject must be null");
        }
        return null;
    }
}
