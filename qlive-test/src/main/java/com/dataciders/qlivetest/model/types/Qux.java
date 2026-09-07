package com.dataciders.qlivetest.model.types;

import de.quinscape.domainql.annotation.GraphQLComputed;
import de.quinscape.domainql.annotation.GraphQLField;
import jakarta.persistence.Column;
import jakarta.persistence.Table;

/**
 * <p>
 *     Hand-written replacement for the generated Qux POJO, which is what a schema type looks like when its
 *     columns alone do not say everything about it.
 * </p>
 * <p>
 *     DomainQL resolves a domain type by its simple name, so registering this one with
 *     {@code objectType()} after the schema's own types puts it in the generated type's place -- for the
 *     query document service as well, which materializes whatever the table lookup names. Extending the
 *     generated POJO is what keeps it able to hold a row: the columns, their JPA annotations and the
 *     fetcher context all come along, and only what is written here is different.
 * </p>
 * <p>
 *     Where it lives follows the split the application is laid out by: {@code domain} is what the code
 *     generator writes, {@code model} what is written by hand, {@code runtime} the code that runs. The
 *     generator owns {@code domain} outright and deletes anything in it that it did not write, so a
 *     hand-written type could not live there even if the convention allowed it.
 * </p>
 */
@Table(name = "qux", schema = "public")
public class Qux
    extends com.dataciders.qlivetest.domain.tables.pojos.Qux
{
    /**
     * A field of the type that no column backs, computed from the row it is read off.
     * <p>
     *     What it computes from are the columns of that row, so a query selecting this should select those
     *     as well -- nothing fetches a column on its account. The query document service leaves a field
     *     like this alone: there is nothing for it to select, and DomainQL reads the property off the
     *     object.
     * </p>
     *
     * @return summary of this row
     */
    @GraphQLComputed
    public String getSummary()
    {
        return getName() + " / " + getStringValue();
    }


    /**
     * Present only so the property is not read-only, which is what a property has to be to become a field
     * at all. Nothing reads what it stores.
     *
     * @param ignored   ignored
     */
    public void setSummary(String ignored)
    {
    }


    /**
     * Amounts are minor units in a bigint column, which is all a column can say. Currency is the scalar
     * that knows what to do with them, and saying so is a property's business.
     *
     * @return currency value
     */
    @Override
    @Column(name = "currency_value")
    @GraphQLField(type = "Currency")
    public Long getCurrencyValue()
    {
        return super.getCurrencyValue();
    }
}
