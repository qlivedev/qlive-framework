package io.github.qlivedev.qlive.graphql.jooq;

import io.github.qlivedev.qlive.api.jooq.GeneratedDomainObject;
import org.jooq.codegen.DefaultGeneratorStrategy;
import org.jooq.meta.Definition;

/**
 * Makes all JOOQ POJOs extend the DomainObject interface.
 */
public class DomainObjectGeneratorStrategy
    extends DefaultGeneratorStrategy
{
    @Override
    public String getJavaClassExtends(Definition definition, Mode mode)
    {
        if ( mode == Mode.POJO)
        {
            return GeneratedDomainObject.class.getName();
        }
        return super.getJavaClassExtends(definition, mode);
    }
}
