package io.github.qlivedev.graphql.config;

import org.jooq.TableField;
import org.svenson.JSONProperty;

/// One end of a many-to-many: the type it is, the column of the link table pointing at it, and the field the
/// type gets listing the rows on the other end.
///
/// @see ManyToManyModel
public class ManyToManyEnd
{
    private final RelationModel relation;

    private final String field;


    /// @param relation     the relation from the link table to this end. It generates no fields of its own.
    /// @param field        name of the field listing the other end's rows on this end's type, or `null` for
    ///                     none
    public ManyToManyEnd(RelationModel relation, String field)
    {
        this.relation = relation;
        this.field = field;
    }


    /// The relation from the link table to this end, which is what the link column and the id it references are
    /// read from.
    @JSONProperty(ignore = true)
    public RelationModel getRelation()
    {
        return relation;
    }


    /// The domain type of this end.
    public String getType()
    {
        return relation.getTargetType();
    }


    /// The property of the link type pointing at this end, e.g. "barId".
    public String getLinkField()
    {
        return relation.getSourceFields().get(0);
    }


    /// The column of the link table pointing at this end.
    @JSONProperty(ignore = true)
    public TableField<?, ?> getLinkColumn()
    {
        return relation.getSourceDBFields().get(0);
    }


    /// The column of this end's table the link column references, its id.
    @JSONProperty(ignore = true)
    public TableField<?, ?> getKeyColumn()
    {
        return relation.getTargetDBFields().get(0);
    }


    /// The property of this end's type the link column references, its id.
    @JSONProperty(ignore = true)
    public String getKeyField()
    {
        return relation.getTargetFields().get(0);
    }


    /// Name of the field on this end's type listing the rows on the other end, e.g. "bazs" on Bar. `null` where
    /// the declaration gave this end none.
    @JSONProperty(ignoreIfNull = true)
    public String getField()
    {
        return field;
    }


    @Override
    public String toString()
    {
        return getType() + "." + field + " <- " + relation.getSourceType() + "." + getLinkField();
    }
}
