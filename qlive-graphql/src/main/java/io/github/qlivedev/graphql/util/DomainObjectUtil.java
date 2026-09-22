package io.github.qlivedev.graphql.util;

import io.github.qlivedev.graphql.DomainQL;
import io.github.qlivedev.graphql.DomainQLException;
import io.github.qlivedev.graphql.TableLookup;
import io.github.qlivedev.graphql.generic.DomainObject;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.InsertQuery;
import org.jooq.StoreQuery;
import org.jooq.Table;
import org.jooq.UpdateQuery;
import org.jooq.impl.DSL;

import static org.jooq.impl.DSL.*;

/**
 * Contains some JOOQ util methods for DomainQL domain objects.
 */
public final class DomainObjectUtil
{
    private DomainObjectUtil()
    {
        // no instances
    }


    /**
     * Inserts the given domain object
     *
     * @param dslContext   DSL context
     * @param domainQL     DomainQL instance
     * @param domainObject domain object
     *
     * @return result count for insert statement
     */
    public static int insert(DSLContext dslContext, DomainQL domainQL, DomainObject domainObject)
    {
        final String domainType = domainObject.getDomainType();
        final Table<?> jooqTable = tableFor(domainQL, domainType);

        final String id = (String) domainObject.getProperty(DomainObject.ID);

        final InsertQuery<?> insertQuery = dslContext.insertQuery(
            jooqTable
        );
        insertQuery.addConditions(
            field(
                name(
                    DomainObject.ID
                )
            )
                .eq(
                    id
                )
        );

        addFieldValues(domainQL, insertQuery, domainObject);

        return insertQuery.execute();
    }


    /**
     * Updates the given domain object by id.
     *
     * @param dslContext   DSL context
     * @param domainQL     DomainQL instance
     * @param domainObject domain object with id
     *
     * @return result count for update statement
     */
    public static int update(DSLContext dslContext, DomainQL domainQL, DomainObject domainObject)
    {
        final String domainType = domainObject.getDomainType();
        final Table<?> jooqTable = tableFor(domainQL, domainType);

        final String id = (String) domainObject.getProperty(DomainObject.ID);

        final UpdateQuery<?> updateQuery = dslContext.updateQuery(
            jooqTable
        );
        updateQuery.addConditions(
            field(
                name(
                    DomainObject.ID
                )
            )
                .eq(
                    id
                )
        );

        addFieldValues(domainQL, updateQuery, domainObject);

        return updateQuery.execute();
    }


    /**
     * Inserts the given domain object or does an update by id.
     * <p>
     * The method will first selectCount() the number of rows with the same id and will decided whether to insert
     * or update based on that.
     * </p>
     *
     * @param dslContext   DSL context
     * @param domainQL     DomainQL instance
     * @param domainObject domain object with id
     *
     * @return result count for update statement
     */
    public static int insertOrUpdate(DSLContext dslContext, DomainQL domainQL, DomainObject domainObject)
    {

        final String domainType = domainObject.getDomainType();

        final Table<?> jooqTable = tableFor(domainQL, domainType);


        final String id = (String) domainObject.getProperty(DomainObject.ID);

        final boolean exists = dslContext.fetchExists(
            dslContext.select()
                .from(jooqTable)
                .where(
                    field(
                        name(
                            DomainObject.ID
                        )
                    )
                        .eq(id)
                ));

        // We use the basic non-DSL JOOQ api here
        final StoreQuery<?> query;
        if (exists)
        {
            return update(dslContext, domainQL, domainObject);
        }
        else
        {
            return insert(dslContext, domainQL, domainObject);
        }
    }


    public static int delete(DSLContext dslContext, DomainQL domainQL, DomainObject domainObject)
    {
        final String domainType = domainObject.getDomainType();
        final String id = (String) domainObject.getProperty(DomainObject.ID);

        return delete(dslContext, domainQL, domainType, id);
    }


    public static int delete(DSLContext dslContext, DomainQL domainQL, String domainType, String id)
    {
        final Table<?> jooqTable = tableFor(domainQL, domainType);

        final int count = dslContext.deleteFrom(jooqTable).where(
            field(
                name(
                    DomainObject.ID
                )
            )
                .eq(id)
        ).execute();

        return count;
    }


    /**
     * Sets all domain object values in a JOOQ query.
     *
     * @param domainQL
     * @param query        insert or update query
     * @param domainObject domain object
     */
    private static void addFieldValues(
        DomainQL domainQL,
        StoreQuery<?> query,
        DomainObject domainObject
    )
    {

        for (String propertyName : domainObject.propertyNames())
        {
            final Field fieldForProp = domainQL.getTypeRegistry().lookupField(
                domainObject.getDomainType(),
                propertyName
            );

            // A property that no column backs is a computed field, and a computed field has nothing to store:
            // it is read off the row the query already selected. Skipping it is what lets an object be read
            // and written back unchanged, which is what this class is for. Only properties carrying a JPA
            // @Column reach the field lookup, so this is the whole of the distinction.
            if (fieldForProp == null)
            {
                continue;
            }

            query.addValue(
                fieldForProp,
                domainObject.getProperty(propertyName)
            );
        }
    }

    /**
     * The table a domain object of the given type is stored in.
     *
     * @param domainQL   DomainQL instance
     * @param domainType domain type name
     *
     * @return jOOQ table
     *
     * @throws DomainQLException if the domain exposes no table under that name
     */
    private static Table<?> tableFor(DomainQL domainQL, String domainType)
    {
        final TableLookup lookup = domainQL.getTypeRegistry().lookupType(domainType);

        if (lookup == null)
        {
            throw new DomainQLException(
                "No table for domain type '" + domainType + "'. This class stores a domain object by writing the " +
                    "table behind its type, so a type the domain has no table for cannot go through it."
            );
        }

        return lookup.getTable();
    }

}
