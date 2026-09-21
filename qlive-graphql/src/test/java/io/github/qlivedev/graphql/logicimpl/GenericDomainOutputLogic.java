package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.generic.DomainObject;
import io.github.qlivedev.graphql.scalar.TimestampScalar;
import io.github.qlivedev.graphql.testdomain.tables.pojos.Foo;

@GraphQLLogic
public class GenericDomainOutputLogic
{
    @GraphQLQuery
    public DomainObject queryDomainObject()
    {
        final Foo foo = new Foo();
        foo.setId("c5a27eec-ab1e-4b66-8af1-544e291eda36");
        foo.setName("FooAsDomainObj");
        foo.setNum(9876);
        foo.setCreated(TimestampScalar.convert("2018-01-01T12:34:56.123Z"));
        return foo;
    }
}
