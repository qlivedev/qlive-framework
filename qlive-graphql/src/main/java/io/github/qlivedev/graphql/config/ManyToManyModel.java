package io.github.qlivedev.graphql.config;

import io.github.qlivedev.graphql.TypeRegistry;
import org.jooq.Table;
import org.svenson.JSONProperty;

/// A declared many-to-many: a link table whose rows each associate a row of one end with a row of the other.
///
/// Each end gets a field listing the rows on the other end, fetched through the link table -- `Bar.bazs` and
/// `Baz.bars` for `bar_link`. The two relations out of the link table that make it are held here and nowhere
/// else: they are not relations of the domain and generate no fields, so an application can still configure the
/// same foreign keys as ordinary relations when it wants the link rows as rows.
///
/// @see io.github.qlivedev.graphql.ManyToManyBuilder
public class ManyToManyModel
{
    private final ManyToManyEnd left;

    private final ManyToManyEnd right;

    private final boolean writable;


    /// @param left         end the first link column points at
    /// @param right        end the second link column points at
    /// @param writable     whether a link row can be inserted from its two link columns alone
    public ManyToManyModel(ManyToManyEnd left, ManyToManyEnd right, boolean writable)
    {
        this.left = left;
        this.right = right;
        this.writable = writable;
    }


    /// The domain type of the link table, e.g. "BarLink".
    public String getLinkType()
    {
        return left.getRelation().getSourceType();
    }


    @JSONProperty(ignore = true)
    public Table<?> getLinkTable()
    {
        return left.getRelation().getSourceTable();
    }


    @JSONProperty(ignore = true)
    public Class<?> getLinkPojoClass()
    {
        return left.getRelation().getSourcePojoClass();
    }


    /// The end the first of the declaration's link columns points at.
    public ManyToManyEnd getLeft()
    {
        return left;
    }


    /// The end the second of the declaration's link columns points at.
    public ManyToManyEnd getRight()
    {
        return right;
    }


    /// Whether the fields of both ends can be written: true where a link row can be inserted given nothing but its
    /// two link columns, because every other column is filled by QLive or the database or may stay null.
    public boolean isWritable()
    {
        return writable;
    }


    /// The through field of the given type with the given name, oriented from that type, or `null` where neither
    /// end is it.
    public ManyToManyField field(String type, String name)
    {
        if (type.equals(left.getType()) && name.equals(left.getField()))
        {
            return new ManyToManyField(this, left, right);
        }
        if (type.equals(right.getType()) && name.equals(right.getField()))
        {
            return new ManyToManyField(this, right, left);
        }
        return null;
    }


    /// The model with both ends resolved against the registered types, which differ from the POJOs where a
    /// hand-written class overrides one.
    ///
    /// @param typeRegistry  type registry
    ///
    /// @return updated model, or this one where nothing changed
    public ManyToManyModel update(TypeRegistry typeRegistry)
    {
        final RelationModel leftRelation = left.getRelation().update(typeRegistry);
        final RelationModel rightRelation = right.getRelation().update(typeRegistry);

        if (leftRelation == left.getRelation() && rightRelation == right.getRelation())
        {
            return this;
        }

        return new ManyToManyModel(
            new ManyToManyEnd(leftRelation, left.getField()),
            new ManyToManyEnd(rightRelation, right.getField()),
            writable
        );
    }


    @Override
    public String toString()
    {
        return super.toString() + ": "
            + "linkType = '" + getLinkType() + '\''
            + ", left = " + left
            + ", right = " + right
            + ", writable = " + writable
            ;
    }
}
