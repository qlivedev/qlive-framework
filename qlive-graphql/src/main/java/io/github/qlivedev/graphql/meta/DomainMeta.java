package io.github.qlivedev.graphql.meta;

import io.github.qlivedev.graphql.GenericTypeReference;
import io.github.qlivedev.graphql.config.RelationModel;
import io.github.qlivedev.util.JSONUtil;
import org.svenson.JSONable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Schema meta data on the server-side. Basically a data map with named keys, "types" being special and containing the
 * type meta data.
 *
 * @see DomainTypeMeta
 */
public class DomainMeta
    implements JSONable
{
    private final Map<String, Object> data;

    /**
     * Name of the builtin name fields type meta
     */
    public final static String NAME_FIELDS = "nameFields";

    /**
     * Name of the builtin relations addendum
     */
    public final static String RELATIONS = "relations";

    /**
     * Name of the builtin generic types addendum
     */
    public final static String GENERIC_TYPES = "genericTypes";

    public DomainMeta(Map<String, DomainTypeMeta> types)
    {

        data = new HashMap<>();
        addAddendum("types", types);
    }


    public <T> void addAddendum(String name, T object)
    {
        final Object existing = data.get(name);
        if (existing != null)
        {
            throw new IllegalStateException("Data key '" + name + "' already set.");
        }
        data.put(name, object);
    }

    public DomainTypeMeta getTypeMeta(String typeName)
    {
        final Map<String, Object> typesMap = (Map<String, Object>) data.get("types");
        final DomainTypeMeta domainTypeMeta = (DomainTypeMeta) typesMap.get(typeName);
        if (domainTypeMeta == null)
        {
            throw new IllegalStateException("Invalid type '" + typeName + "'");
        }
        return domainTypeMeta;
    }


    public Map<String, Object> getData()
    {
        return data;
    }


    public List<RelationModel> getRelationModels()
    {
        return (List<RelationModel>) data.get(RELATIONS);
    }

    public List<GenericTypeReference> getGenericTypes()
    {
        return (List<GenericTypeReference>) data.get(GENERIC_TYPES);
    }

    @Override
    public String toJSON()
    {
        return JSONUtil.DEFAULT_GENERATOR.forValue(data);
    }
}
