package io.github.qlivedev.graphql.generic;


/**
 * Creates a new instance of the a given GraphQL schema type.
 *
 */
public interface DomainObjectFactory
{
    /**
     * Creates a new domain object instance for the given type name.
     *
     * @param type      type name
     *                  
     * @return new domain object instance
     */
    DomainObject create(String type) throws DomainObjectCreationException;
}
