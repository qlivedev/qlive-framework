package com.dataciders.qlivetest.types;

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
 *     It lives outside the generated package on purpose. The jOOQ code generator owns
 *     {@code com.dataciders.qlivetest.domain} and deletes anything in it that it did not write.
 * </p>
 */
@Table(name = "qux", schema = "public")
public class Qux
    extends com.dataciders.qlivetest.domain.tables.pojos.Qux
{
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
