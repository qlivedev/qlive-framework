package com.dataciders.qlivetest.model.types;

import de.quinscape.domainql.annotation.GraphQLComputed;
import de.quinscape.domainql.annotation.GraphQLField;
import jakarta.persistence.Column;
import jakarta.persistence.Table;

///
///     Hand-written replacement for the generated Qux POJO, which is what a schema type looks like when its
///     columns alone do not say everything about it.
///
///
///     DomainQL resolves a domain type by its simple name, so registering this one with
///     `objectType()` after the schema's own types puts it in the generated type's place -- for the
///     query document service as well, which materializes whatever the table lookup names. Extending the
///     generated POJO is what keeps it able to hold a row: the columns, their JPA annotations and the
///     fetcher context all come along, and only what is written here is different.
///
///
///     What documents this type to the schema is not the Javadoc here but the handwritten
///     `src/main/resources/domain-typedocs.json`. The extraction feeding `source-typedocs.json`
///     reads a class's own source and sees only the members declared in it, so documenting a column would
///     mean overriding its getter for no reason but to hang a comment on it. Type docs merge by whole type
///     with the last source winning, and the extracted docs are read after the handwritten ones, so the two
///     cannot each document a part of this type: moving the computed field's documentation to where it is
///     written would take every column's along with it.
///
///
///     Where it lives follows the split the application is laid out by: `domain` is what the code
///     generator writes, `model` what is written by hand, `runtime` the code that runs. The
///     generator owns `domain` outright and deletes anything in it that it did not write, so a
///     hand-written type could not live there even if the convention allowed it.
///
@Table(name = "qux", schema = "public")
public class Qux
    extends com.dataciders.qlivetest.domain.tables.pojos.Qux
{
    /// A field of the type that no column backs, computed from the row it is read off.
    ///
    ///     What it computes from are the columns of that row, so a query selecting this should select those
    ///     as well -- nothing fetches a column on its account. The query document service leaves a field
    ///     like this alone: there is nothing for it to select, and DomainQL reads the property off the
    ///     object.
    ///
    ///
    /// @return summary of this row
    @GraphQLComputed
    public String getSummary()
    {
        return getName() + " / " + getStringValue();
    }


    /// Present only so the property is not read-only, which is what a property has to be to become a field
    /// at all. Nothing reads what it stores.
    ///
    /// @param ignored   ignored
    public void setSummary(String ignored)
    {
    }


    /// Currency value.
    ///
    /// Defined as integer containing 1/10000th currency units. So 10000 would be 1 EUR, e.g.
    ///
    /// The client holds these in a JavaScript number, which represents every integer exactly up to
    /// Number.MAX_SAFE_INTEGER, 9007199254740991 -- 900,719,925,474.0991 EUR. Up to there the value carries
    /// exactly, with no error at all. Above it the column stays a long, but the client sees only every
    /// second value, then every fourth, and so on: what an amount rounds to is off by up to half that step.
    ///
    /// What is lost there is resolution, not magnitude. A double keeps 53 significant bits whatever the
    /// exponent, so even at the top of the long range the value is wrong by less than 2^-53 of itself,
    /// around 0.00000000000001%. The step is what grows, doubling with every doubling of the amount:
    /// 1/10000th EUR just past MAX_SAFE_INTEGER, a cent above ~57 trillion EUR, and 1024 units -- about 5
    /// cents -- approaching the end of the long range at 922,337,203,685,477.6250 EUR.
    ///
    /// @return currency value
    @Override
    @Column(name = "currency_value")
    @GraphQLField(type = "Currency")
    public Long getCurrencyValue()
    {
        return super.getCurrencyValue();
    }
}
