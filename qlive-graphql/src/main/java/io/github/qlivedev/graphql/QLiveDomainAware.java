package io.github.qlivedev.graphql;

/**
 * Implemented by GraphQL scalar implementations that need to know about the GraphQL schema and the meta data of
 * the domain they are part of.
 */
public interface QLiveDomainAware
{
    /**
     * Provides the domain the scalar is registered in.
     *
     * @param domain the assembled domain
     */
    void setDomain(QLiveDomain domain);
}
