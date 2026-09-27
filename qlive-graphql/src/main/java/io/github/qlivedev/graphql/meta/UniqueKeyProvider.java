package io.github.qlivedev.graphql.meta;

import io.github.qlivedev.graphql.PojoTypes;
import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.graphql.TableLookup;
import io.github.qlivedev.graphql.TypeRegistry;
import io.github.qlivedev.util.JSONUtil;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UniqueKey;
import org.svenson.info.JSONPropertyInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the unique constraints of every table-backed domain type into its type meta data, the primary key first.
 * <p>
 * The constraints come from the jOOQ tables, which carry them as generated metadata, so nothing here asks the
 * database. Unique indexes that are not constraints are not in that metadata and so are not described.
 * <p>
 * A constraint is described by the domain type's field names, not by column names. One with a column that no field of
 * the type maps to cannot be expressed that way and is left out.
 *
 * @see UniqueKeyMeta
 */
public class UniqueKeyProvider
    implements MetadataProvider
{
    @Override
    public void provideMetaData(QLiveDomain domain, DomainMeta meta)
    {
        final TypeRegistry registry = domain.getTypeRegistry();

        for (Map.Entry<String, TableLookup> entry : registry.getJooqTables().entrySet())
        {
            final String domainType = entry.getKey();
            final TableLookup lookup = entry.getValue();
            final Table<?> table = lookup.getTable();

            final Map<String, String> fieldsByColumn = fieldsByColumn(registry, domainType, lookup.getPojoType());

            final UniqueKey<?> primaryKey = table.getPrimaryKey();
            final List<UniqueKeyMeta> keys = new ArrayList<>();
            if (primaryKey != null)
            {
                addKey(keys, primaryKey, true, fieldsByColumn);
            }
            for (UniqueKey<?> key : table.getUniqueKeys())
            {
                addKey(keys, key, false, fieldsByColumn);
            }

            if (!keys.isEmpty())
            {
                meta.getTypeMeta(domainType).setMeta(DomainMeta.UNIQUE_KEYS, keys);
            }
        }
    }


    private static Map<String, String> fieldsByColumn(TypeRegistry registry, String domainType, Class<?> pojoType)
    {
        final Map<String, String> map = new HashMap<>();
        for (JSONPropertyInfo info : JSONUtil.getClassInfo(pojoType).getPropertyInfos())
        {
            if (!PojoTypes.isNormalProperty(info))
            {
                continue;
            }

            final String property = info.getJsonName();
            final Field<?> column = registry.lookupField(domainType, property);
            if (column != null)
            {
                map.put(column.getName(), property);
            }
        }
        return map;
    }


    private static void addKey(
        List<UniqueKeyMeta> keys,
        UniqueKey<?> key,
        boolean primary,
        Map<String, String> fieldsByColumn
    )
    {
        final List<String> fields = new ArrayList<>();
        boolean nullable = false;
        for (Field<?> column : key.getFields())
        {
            final String field = fieldsByColumn.get(column.getName());
            if (field == null)
            {
                return;
            }
            fields.add(field);
            nullable |= column.getDataType().nullable();
        }

        keys.add(new UniqueKeyMeta(key.getName(), fields, primary, nullable));
    }
}
