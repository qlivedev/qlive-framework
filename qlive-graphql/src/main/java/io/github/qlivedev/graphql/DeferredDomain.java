package io.github.qlivedev.graphql;

import java.util.function.Supplier;

/**
 * The domain, for the things that are built before it exists.
 * <p>
 * Queries and mutations are created while the schema is still being assembled and read the domain only at fetch
 * time, long after. This is what they are handed instead of the assembler building it, and it refuses to answer
 * until there is a whole domain to answer with.
 */
final class DeferredDomain
    implements Supplier<QLiveDomain>
{
    /// Written once, at the end of the assembly, and read from whatever thread serves a request afterwards.
    private volatile QLiveDomain domain;


    void provide(QLiveDomain domain)
    {
        this.domain = domain;
    }


    @Override
    public QLiveDomain get()
    {
        if (domain == null)
        {
            throw new DomainQLException(
                "The domain is not assembled yet. Nothing in the schema build has a domain to read: it is what " +
                    "the build produces."
            );
        }
        return domain;
    }
}
