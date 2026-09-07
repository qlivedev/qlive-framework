package com.dataciders.qlive.testmodel.types;

import jakarta.persistence.Table;

/// Hand-written replacement for the generated TestFoo, here to be the case of a domain type with a field
/// no column backs.
///
/// The package follows the split an application is laid out by: the generated types live in `testdomain`,
/// the hand-written ones here.
@Table(name = "test_foo", schema = "public")
public class TestFoo
    extends com.dataciders.qlive.testdomain.tables.pojos.TestFoo
{
    private String summary;


    /// A field of the type that is no column of the table. Nothing fills it here -- what matters for the
    /// test is that a query document can select it without the planner looking for a column called
    /// "summary".
    public String getSummary()
    {
        return summary;
    }


    public void setSummary(String summary)
    {
        this.summary = summary;
    }
}
