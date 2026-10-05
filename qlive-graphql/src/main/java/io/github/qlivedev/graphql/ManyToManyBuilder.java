package io.github.qlivedev.graphql;

import io.github.qlivedev.graphql.config.ManyToManyEnd;
import io.github.qlivedev.graphql.config.ManyToManyModel;
import io.github.qlivedev.graphql.config.Options;
import io.github.qlivedev.graphql.config.RelationModel;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/// Declares a many-to-many: a link table, its two columns pointing at the two ends, and the field each end gets
/// listing the rows on the other.
///
/// The link columns are named either as the foreign keys backing them or, for a link table without
/// foreign-key constraints, as properties of the link type. Both declare the same thing, and nothing after the
/// declaration can tell them apart.
///
/// ```java
/// new ManyToManyBuilder()
///     .withForeignKeyFields(BAR_LINK.BAR_ID, BAR_LINK.BAZ_ID)
///     .withFieldNames("bazs", "bars")
/// ```
///
/// Each link column has to reference the `id` of its end, which is what the working set names rows by.
///
/// @see QLiveDomainBuilder#withManyToMany(ManyToManyBuilder)
/// @see QLiveDomainBuilder#configureManyToMany(TableField, TableField, String, String)
public class ManyToManyBuilder
{
    private final static Logger log = LoggerFactory.getLogger(ManyToManyBuilder.class);

    /// Name of the property every end is referenced by.
    private final static String ID = "id";

    /// Name of the property the merge writes for a versioned row, which is therefore never in the way of an
    /// insert.
    private final static String VERSION = "version";

    private RelationBuilder left;

    private RelationBuilder right;

    private String fieldName;

    private String otherFieldName;


    /// Names the link columns as the foreign keys backing them.
    ///
    /// @param linkField        link column pointing at the first end, which gets the field named first
    /// @param otherLinkField   link column pointing at the other end
    ///
    /// @return this builder
    public ManyToManyBuilder withForeignKeyFields(TableField<?, ?> linkField, TableField<?, ?> otherLinkField)
    {
        requireUndeclared();

        Objects.requireNonNull(linkField, "linkField can't be null");
        Objects.requireNonNull(otherLinkField, "otherLinkField can't be null");

        if (!linkField.getTable().equals(otherLinkField.getTable()))
        {
            throw new QLiveDomainBuilderException(
                "The link columns of a many-to-many must belong to the same table: Encountered " +
                    linkField.getTable() + " and " + otherLinkField.getTable()
            );
        }

        left = new RelationBuilder().withForeignKeyFields(linkField);
        right = new RelationBuilder().withForeignKeyFields(otherLinkField);

        return this;
    }


    /// Names the link columns as properties of the link type, for a link table without foreign-key constraints.
    ///
    /// @param linkPojo         POJO class of the link table
    /// @param linkField        property of the link type pointing at the first end
    /// @param type             POJO class of the first end, which gets the field named first
    /// @param otherLinkField   property of the link type pointing at the other end
    /// @param otherType        POJO class of the other end
    ///
    /// @return this builder
    public ManyToManyBuilder withPojoFields(
        Class<?> linkPojo,
        String linkField,
        Class<?> type,
        String otherLinkField,
        Class<?> otherType
    )
    {
        requireUndeclared();

        Objects.requireNonNull(linkPojo, "linkPojo can't be null");
        Objects.requireNonNull(linkField, "linkField can't be null");
        Objects.requireNonNull(type, "type can't be null");
        Objects.requireNonNull(otherLinkField, "otherLinkField can't be null");
        Objects.requireNonNull(otherType, "otherType can't be null");

        left = new RelationBuilder().withPojoFields(linkPojo, List.of(linkField), type, List.of(ID));
        right = new RelationBuilder().withPojoFields(linkPojo, List.of(otherLinkField), otherType, List.of(ID));

        return this;
    }


    /// Names the through fields. `null` leaves that end without one, but one of the two has to be given.
    ///
    /// @param fieldName        field on the first end, listing the rows of the other, e.g. "bazs" on Bar
    /// @param otherFieldName   field on the other end, listing the rows of the first, e.g. "bars" on Baz
    ///
    /// @return this builder
    public ManyToManyBuilder withFieldNames(String fieldName, String otherFieldName)
    {
        this.fieldName = fieldName;
        this.otherFieldName = otherFieldName;
        return this;
    }


    private void requireUndeclared()
    {
        if (left != null)
        {
            throw new QLiveDomainBuilderException("The link columns of this many-to-many are already declared");
        }
    }


    ManyToManyModel build(
        Map<String, TableLookup> jooqTables,
        Map<String, Field<?>> fieldLookup,
        Options options
    )
    {
        if (left == null)
        {
            throw new QLiveDomainBuilderException(
                "A many-to-many needs its link columns: call .withForeignKeyFields() or .withPojoFields()"
            );
        }

        if (fieldName == null && otherFieldName == null)
        {
            throw new QLiveDomainBuilderException(
                "A many-to-many needs a field on at least one of its ends: call .withFieldNames()"
            );
        }

        // the halves are no relations of the domain, so their ids need not be unique among those
        final Set<String> ids = new HashSet<>();
        final RelationModel leftRelation = left.build(jooqTables, fieldLookup, options, ids);
        final RelationModel rightRelation = right.build(jooqTables, fieldLookup, options, ids);

        if (!leftRelation.getSourceTable().equals(rightRelation.getSourceTable()))
        {
            throw new QLiveDomainBuilderException(
                "The link columns of a many-to-many must belong to the same table: Encountered " +
                    leftRelation.getSourceTable() + " and " + rightRelation.getSourceTable()
            );
        }

        if (leftRelation.getSourceDBFields().equals(rightRelation.getSourceDBFields()))
        {
            throw new QLiveDomainBuilderException(
                "The link columns of a many-to-many must differ, not both be " +
                    leftRelation.getSourceType() + "." + leftRelation.getSourceFields()
            );
        }

        requireIdReference(leftRelation);
        requireIdReference(rightRelation);

        if (
            leftRelation.getTargetType().equals(rightRelation.getTargetType()) &&
                Objects.equals(fieldName, otherFieldName)
        )
        {
            throw new QLiveDomainBuilderException(
                "Both ends of the many-to-many through " + leftRelation.getSourceType() + " are " +
                    leftRelation.getTargetType() + ", so their fields need different names, not both '" +
                    fieldName + "'"
            );
        }

        final ManyToManyModel model = new ManyToManyModel(
            new ManyToManyEnd(leftRelation, fieldName),
            new ManyToManyEnd(rightRelation, otherFieldName),
            isInsertableFromLinks(leftRelation, rightRelation, fieldLookup)
        );

        warnWithoutUniquePair(model);

        return model;
    }


    /// Refuses a link column that references anything but the single `id` column of its end.
    private static void requireIdReference(RelationModel relation)
    {
        if (relation.getSourceFields().size() != 1 || !relation.getTargetFields().equals(List.of(ID)))
        {
            throw new QLiveDomainBuilderException(
                "The link column " + relation.getSourceType() + "." + relation.getSourceFields() +
                    " has to reference the id of " + relation.getTargetType() + ", not " +
                    relation.getTargetFields()
            );
        }
    }


    /// Whether a row of the link table can be inserted given only its two link columns: every other column is the
    /// id or the version, which QLive fills in, or may stay null, or has a default.
    ///
    /// An id QLive fills in is a string one, which it generates the way the client does for a new row. Any other id
    /// needs a default.
    private static boolean isInsertableFromLinks(
        RelationModel left,
        RelationModel right,
        Map<String, Field<?>> fieldLookup
    )
    {
        final Table<?> linkTable = left.getSourceTable();
        final String linkType = left.getSourceType();

        final Set<String> linkColumns = Set.of(
            left.getSourceDBFields().get(0).getName(),
            right.getSourceDBFields().get(0).getName()
        );

        for (Field<?> column : linkTable.fields())
        {
            if (
                linkColumns.contains(column.getName()) ||
                    column.getDataType().nullable() ||
                    column.getDataType().defaulted()
            )
            {
                continue;
            }

            final String property = propertyOf(fieldLookup, linkType, column);

            if (VERSION.equals(property) || (ID.equals(property) && column.getType() == String.class))
            {
                continue;
            }

            log.debug(
                "Many-to-many through {} is read-only: column '{}' needs a value of its own",
                linkType,
                column.getName()
            );
            return false;
        }
        return true;
    }


    /// The property of the given type the given column backs, or `null` where none does.
    private static String propertyOf(Map<String, Field<?>> fieldLookup, String domainType, Field<?> column)
    {
        final String prefix = QLiveDomainBuilder.fieldLookupKey(domainType, "");

        for (Map.Entry<String, Field<?>> e : fieldLookup.entrySet())
        {
            if (e.getKey().startsWith(prefix) && e.getValue().getName().equals(column.getName()))
            {
                return e.getKey().substring(prefix.length());
            }
        }
        return null;
    }


    /// Warns where no unique key of the link table is the pair of link columns. Without one, adding an association
    /// that exists has to look before it inserts, and two concurrent additions can both find nothing.
    private static void warnWithoutUniquePair(ManyToManyModel model)
    {
        final Set<String> pair = Set.of(
            model.getLeft().getLinkColumn().getName(),
            model.getRight().getLinkColumn().getName()
        );

        for (UniqueKey<?> key : model.getLinkTable().getKeys())
        {
            final Set<String> columns = key.getFields().stream().map(Field::getName).collect(Collectors.toSet());
            if (columns.equals(pair))
            {
                return;
            }
        }

        log.warn(
            "No unique key on {} covers exactly the link columns {}. Concurrent additions of the same association " +
                "can create it twice.",
            model.getLinkTable().getName(),
            pair
        );
    }
}
