package io.github.qlivedev.graphql;

/// Implemented by GraphQL scalar implementations or scalar coercings that need to know about the GraphQL schema and the
/// metadata of the domain they are part of.o
public interface QLiveDomainAware
{
    /// Provides the domain the scalar is registered in.
    ///
    /// @param domain the assembled domain
    void setDomain(QLiveDomain domain);
}
