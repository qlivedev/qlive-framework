package io.github.qlivedev.graphql.fetcher;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.github.qlivedev.graphql.config.ManyToManyField;
import io.github.qlivedev.graphql.generic.DomainObject;
import io.github.qlivedev.util.JSONUtil;
import org.jooq.DSLContext;
import org.jooq.Table;
import org.jooq.TableField;

import java.util.List;

/// Fetches the rows on the other end of a many-to-many, through the link table.
public class ManyToManyFetcher
    implements DataFetcher<Object>
{
    private final DSLContext dslContext;

    private final ManyToManyField field;


    public ManyToManyFetcher(DSLContext dslContext, ManyToManyField field)
    {
        this.dslContext = dslContext;
        this.field = field;
    }


    @Override
    @SuppressWarnings("unchecked")
    public Object get(DataFetchingEnvironment environment)
    {
        FetcherContext fetcherContext;
        final Object source = environment.getSource();

        if (source instanceof DomainObject && (fetcherContext = ((DomainObject) source).lookupFetcherContext()) != null)
        {
            return fetcherContext.getProperty(field.name());
        }

        final Object id = JSONUtil.DEFAULT_UTIL.getProperty(source, field.own().getKeyField());
        if (id == null)
        {
            return List.of();
        }

        final Table<?> other = field.other().getRelation().getTargetTable();
        final Table<?> link = field.model().getLinkTable();

        final TableField<?, Object> ownLink = (TableField<?, Object>) field.own().getLinkColumn();
        final TableField<?, Object> otherLink = (TableField<?, Object>) field.other().getLinkColumn();
        final TableField<?, Object> otherKey = (TableField<?, Object>) field.other().getKeyColumn();

        return dslContext.select(other.fields())
            .from(other)
            .join(link).on(otherLink.eq(otherKey))
            .where(ownLink.eq(id))
            .orderBy(other.getPrimaryKey() != null ? other.getPrimaryKey().getFields() : List.of())
            .fetchInto(field.other().getRelation().getTargetPojoClass());
    }


    public ManyToManyField getField()
    {
        return field;
    }
}
