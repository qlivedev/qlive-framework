package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.GraphQLLogic;
import io.github.qlivedev.qlive.api.annotation.GraphQLQuery;
import io.github.qlivedev.qlive.api.generic.DomainObject;
import io.github.qlivedev.qlive.graphql.scalar.TimestampScalar;
import io.github.qlivedev.qlive.graphql.testdomain.tables.pojos.Foo;

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
