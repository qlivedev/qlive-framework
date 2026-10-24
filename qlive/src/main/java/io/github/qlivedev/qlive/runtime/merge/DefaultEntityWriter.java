package io.github.qlivedev.qlive.runtime.merge;

import io.github.qlivedev.qlive.api.generic.GenericScalar;
import io.github.qlivedev.qlive.api.json.JSONUtil;
import io.github.qlivedev.qlive.graphql.OutputType;
import io.github.qlivedev.qlive.graphql.QLiveDomain;
import io.github.qlivedev.qlive.graphql.TableLookup;
import io.github.qlivedev.qlive.graphql.TypeRegistry;
import io.github.qlivedev.qlive.model.merge.EntityChange;
import io.github.qlivedev.qlive.model.merge.EntityDeletion;
import io.github.qlivedev.qlive.model.merge.FieldChange;
import io.github.qlivedev.qlive.model.merge.LinkChange;
import io.github.qlivedev.qlive.model.merge.MergeConfig;
import io.github.qlivedev.qlive.model.merge.MergeResult;
import io.github.qlivedev.qlive.model.merge.MergeStatus;
import io.github.qlivedev.qlive.runtime.QLiveException;
import io.github.qlivedev.qlive.runtime.action.Invariants;
import io.github.qlivedev.qlive.runtime.action.Rule;
import io.github.qlivedev.qlive.runtime.meta.MergeMeta;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UniqueKey;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/// Default {@link EntityWriter}: each call becomes a change, a deletion or a link change for
/// {@link MergeService#mergeInTransaction}, with the POJO's values as the field values.
public class DefaultEntityWriter
    implements EntityWriter
{
    private final Supplier<QLiveDomain> domain;

    private final DSLContext dslContext;

    private final Supplier<MergeService> mergeService;

    private final Map<Class<?>, RowType> rowTypes = new ConcurrentHashMap<>();


    /// The domain and the merge service are asked for on the first write. Logic beans inject the writer, and the
    /// domain is built from the logic beans.
    ///
    /// @param domain       the domain, once it is built
    /// @param dslContext   JOOQ context
    /// @param mergeService the merge service, once it is built
    public DefaultEntityWriter(
        Supplier<QLiveDomain> domain,
        DSLContext dslContext,
        Supplier<MergeService> mergeService
    )
    {
        this.domain = domain;
        this.dslContext = dslContext;
        this.mergeService = mergeService;
    }


    @Override
    public void insert(Object row)
    {
        final RowType rowType = rowType(row);

        Object id = property(row, rowType.idName());
        if (id == null)
        {
            if (!String.class.equals(rowType.idColumn().getType()))
            {
                throw new QLiveException(
                    "No id in " + row + ". Ids are generated for string keys only; '" + rowType.name() + "' has " +
                        rowType.idColumn().getType().getName() + " ones."
                );
            }
            id = UUID.randomUUID().toString();
            JSONUtil.DEFAULT_UTIL.setProperty(row, rowType.idName(), id);
        }

        final List<FieldChange> fields = new ArrayList<>();
        for (Map.Entry<Field<?>, String> column : rowType.namesByColumn().entrySet())
        {
            final Object value = property(row, column.getValue());
            if (value != null && !column.getKey().equals(rowType.idColumn()) &&
                !column.getKey().equals(rowType.versionColumn()))
            {
                fields.add(fieldChange(rowType, column.getValue(), value));
            }
        }

        final EntityChange change = new EntityChange();
        change.setType(rowType.name());
        change.setId(String.valueOf(id));
        change.setNew(true);
        change.setChanges(fields);

        merge(List.of(change), List.of(), List.of());
        readVersion(rowType, row, id);
    }


    @Override
    public void update(Object row, Field<?>... fields)
    {
        final RowType rowType = rowType(row);

        if (fields.length == 0)
        {
            throw new QLiveException("An update of " + row + " names no field to write.");
        }

        final List<FieldChange> changes = new ArrayList<>();
        for (Field<?> field : fields)
        {
            final String name = rowType.namesByColumn().get(field);
            if (name == null)
            {
                throw new QLiveException(
                    "Field " + field + " is no column of '" + rowType.name() + "', the type of " + row + "."
                );
            }
            changes.add(fieldChange(rowType, name, property(row, name)));
        }
        addInvariantFields(rowType, row, changes);

        final Object id = requireId(rowType, row);

        final EntityChange change = new EntityChange();
        change.setType(rowType.name());
        change.setId(String.valueOf(id));
        change.setVersion(version(rowType, row));
        change.setChanges(changes);

        merge(List.of(change), List.of(), List.of());
        readVersion(rowType, row, id);
    }


    @Override
    public void delete(Object row)
    {
        final RowType rowType = rowType(row);

        final EntityDeletion deletion = new EntityDeletion();
        deletion.setType(rowType.name());
        deletion.setId(String.valueOf(requireId(rowType, row)));
        deletion.setVersion(version(rowType, row));

        merge(List.of(), List.of(), List.of(deletion));
    }


    @Override
    public void link(Object row, String field, Object... others)
    {
        final LinkChange change = linkChange(row, field);
        change.setAdded(ids(others));
        change.setRemoved(List.of());

        merge(List.of(), List.of(change), List.of());
    }


    @Override
    public void unlink(Object row, String field, Object... others)
    {
        final LinkChange change = linkChange(row, field);
        change.setAdded(List.of());
        change.setRemoved(ids(others));

        merge(List.of(), List.of(change), List.of());
    }


    private void merge(List<EntityChange> changes, List<LinkChange> links, List<EntityDeletion> deletions)
    {
        final MergeResult result = mergeService.get().mergeInTransaction(changes, links, deletions, new MergeConfig());

        if (result.getStatus() == MergeStatus.CONFLICT)
        {
            throw new MergeConflictException(result.getConflicts());
        }
        if (result.getStatus() == MergeStatus.REFUSED)
        {
            throw new MergeRefusedException(result.getRefusals());
        }
    }


    /// Adds what the invariants reading a written field read besides, with the POJO's values: the merge checks an
    /// invariant on the row as the change sends it.
    private void addInvariantFields(RowType rowType, Object row, List<FieldChange> changes)
    {
        final Set<String> named = new HashSet<>();
        changes.forEach(change -> named.add(change.getField()));

        final Set<String> added = new LinkedHashSet<>();
        for (Rule rule : Invariants.of(domain.get(), rowType.name()))
        {
            final Set<String> fields = Invariants.fields(rule);
            if (fields.stream().anyMatch(named::contains))
            {
                fields.stream().filter(field -> !named.contains(field)).forEach(added::add);
            }
        }
        for (String name : added)
        {
            changes.add(fieldChange(rowType, name, property(row, name)));
        }
    }


    private LinkChange linkChange(Object row, String field)
    {
        final RowType rowType = rowType(row);

        final LinkChange change = new LinkChange();
        change.setType(rowType.name());
        change.setId(String.valueOf(requireId(rowType, row)));
        change.setField(field);
        return change;
    }


    private List<String> ids(Object[] rows)
    {
        final List<String> ids = new ArrayList<>(rows.length);
        for (Object row : rows)
        {
            ids.add(String.valueOf(requireId(rowType(row), row)));
        }
        return ids;
    }


    /// The version the row's POJO was written at, which the next write to it holds to.
    private void readVersion(RowType rowType, Object row, Object id)
    {
        if (rowType.versionColumn() == null)
        {
            return;
        }

        final Object version = dslContext.select(rowType.versionColumn())
            .from(rowType.table())
            .where(any(rowType.idColumn()).eq(rowType.idColumn().getDataType().convert(id)))
            .fetchOne(0);

        JSONUtil.DEFAULT_UTIL.setProperty(row, MergeMeta.VERSION, version);
    }


    private static String version(RowType rowType, Object row)
    {
        if (rowType.versionColumn() == null)
        {
            return null;
        }
        final Object version = property(row, MergeMeta.VERSION);
        return version == null ? null : String.valueOf(version);
    }


    private static Object requireId(RowType rowType, Object row)
    {
        final Object id = property(row, rowType.idName());
        if (id == null)
        {
            throw new QLiveException("No id in " + row + ". Only a stored row can be written to or linked.");
        }
        return id;
    }


    private static FieldChange fieldChange(RowType rowType, String name, Object value)
    {
        final FieldChange change = new FieldChange();
        change.setField(name);
        change.setValue(new GenericScalar(rowType.scalarsByName().get(name), value));
        return change;
    }


    private static Object property(Object row, String name)
    {
        return JSONUtil.DEFAULT_UTIL.getProperty(row, name);
    }


    /// A table-backed domain type as the writer needs it, by the POJO class it is exposed as.
    ///
    /// @param name             GraphQL type name
    /// @param table            its table
    /// @param idColumn         the single-column primary key
    /// @param idName           field name of the id
    /// @param versionColumn    the version column, or `null` where the type is not versioned
    /// @param namesByColumn    field name per column, for the fields a column backs and a scalar types
    /// @param scalarsByName    scalar name per field name
    private record RowType(
        String name,
        Table<?> table,
        Field<?> idColumn,
        String idName,
        Field<?> versionColumn,
        Map<Field<?>, String> namesByColumn,
        Map<String, String> scalarsByName
    )
    {
    }


    private RowType rowType(Object row)
    {
        if (row == null)
        {
            throw new QLiveException("No row to write.");
        }
        return rowTypes.computeIfAbsent(row.getClass(), this::createRowType);
    }


    private RowType createRowType(Class<?> cls)
    {
        final QLiveDomain domain = this.domain.get();
        final TypeRegistry types = domain.getTypeRegistry();

        final OutputType outputType = types.lookup(cls);
        final TableLookup lookup = outputType == null ? null : types.lookupType(outputType.getName());
        if (lookup == null)
        {
            throw new QLiveException(
                cls.getName() + " is no table-backed type of the domain. The writer writes the POJOs the domain " +
                    "exposes its tables as."
            );
        }

        final String name = outputType.getName();
        final Table<?> table = lookup.getTable();

        final UniqueKey<?> primaryKey = table.getPrimaryKey();
        if (primaryKey == null || primaryKey.getFields().size() != 1)
        {
            throw new QLiveException(
                "Type '" + name + "' has no single-column primary key and cannot be written through the merge, " +
                    "which names every row by one id."
            );
        }
        final Field<?> idColumn = primaryKey.getFields().get(0);

        final Map<Field<?>, String> namesByColumn = new LinkedHashMap<>();
        final Map<String, String> scalarsByName = new LinkedHashMap<>();

        final GraphQLObjectType objectType = (GraphQLObjectType) domain.getGraphQLSchema().getType(name);
        for (GraphQLFieldDefinition definition : objectType.getFieldDefinitions())
        {
            final Field<?> column = types.lookupField(name, definition.getName());
            final GraphQLType fieldType = GraphQLTypeUtil.unwrapNonNull(definition.getType());

            if (column != null && fieldType instanceof GraphQLScalarType scalar)
            {
                namesByColumn.put(column, definition.getName());
                scalarsByName.put(definition.getName(), scalar.getName());
            }
        }

        final String idName = namesByColumn.get(idColumn);
        if (idName == null)
        {
            throw new QLiveException("Type '" + name + "' exposes no field for its primary key " + idColumn + ".");
        }
        final Field<?> versionColumn = MergeMeta.isVersioned(domain, name)
            ? types.lookupField(name, MergeMeta.VERSION)
            : null;

        return new RowType(
            name,
            table,
            idColumn,
            idName,
            versionColumn,
            Map.copyOf(namesByColumn),
            Map.copyOf(scalarsByName)
        );
    }


    @SuppressWarnings("unchecked")
    private static Field<Object> any(Field<?> field)
    {
        return (Field<Object>) field;
    }
}
