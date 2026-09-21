package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.generic.DomainObject;
import io.github.qlivedev.graphql.testdomain.tables.pojos.Bar;

@GraphQLLogic
public class NullPropInDomainObjectLogic
{

    @GraphQLQuery
    public DomainObject fetch(DomainObject in, String name)
    {
        final Bar foo = new Bar();

        foo.setName(name);
        foo.setId("38fb6d2b-4946-4b96-8912-bfe81cce2fc0");
        foo.setOwnerId("3ef7126b-ac62-4cb9-a01c-684eaeeb6b3a");

        return foo;
    }
}
