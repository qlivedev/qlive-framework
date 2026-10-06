package io.github.qlivedev.graphql;

import io.github.qlivedev.graphql.generic.GenericScalar;
import io.github.qlivedev.graphql.meta.NameFieldProvider;
import io.github.qlivedev.graphql.testdomain.tables.pojos.Foo;
import io.github.qlivedev.graphql.testdomain.tables.records.FooRecord;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// The jOOQ generator writes a Table, a Record and a POJO of the same simple name, and only the POJO is a
/// domain type. The others are rejected where the application hands them in.
class PojoTypesTest
{
    @Test
    void acceptsThePojo()
    {
        assertThat(PojoTypes.ensurePojoType(Foo.class), is(sameInstance(Foo.class)));
    }


    @Test
    void rejectsTheTable()
    {
        final QLiveDomainTypeException e = assertThrows(
            QLiveDomainTypeException.class,
            () -> PojoTypes.ensurePojoType(io.github.qlivedev.graphql.testdomain.tables.Foo.class)
        );

        assertThat(e.getMessage(), containsString("tables.Foo"));
        assertThat(e.getMessage(), containsString("wrong class"));
    }


    @Test
    void rejectsTheRecord()
    {
        assertThrows(QLiveDomainTypeException.class, () -> PojoTypes.ensurePojoType(FooRecord.class));
    }


    @Test
    void rejectsAScalar()
    {
        final QLiveDomainTypeException e = assertThrows(
            QLiveDomainTypeException.class,
            () -> PojoTypes.ensurePojoType(GenericScalar.class)
        );

        assertThat(e.getMessage(), containsString("withAdditionalScalar"));
    }


    /// Where the application declares it, not when the domain is built and nothing points back at the import.
    @Test
    void rejectsTheTableWhereItIsDeclared()
    {
        assertThrows(
            QLiveDomainTypeException.class,
            () -> NameFieldProvider.newProvider().forType(io.github.qlivedev.graphql.testdomain.tables.Foo.class)
        );
        assertThrows(
            QLiveDomainTypeException.class,
            () -> QLiveDomainBuilder.newDomain(null).withAdditionalInputType(FooRecord.class)
        );
    }
}
