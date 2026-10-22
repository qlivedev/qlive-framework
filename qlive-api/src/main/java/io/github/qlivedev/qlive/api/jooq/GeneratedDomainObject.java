package io.github.qlivedev.qlive.api.jooq;

import io.github.qlivedev.qlive.api.fetcher.FetcherContext;
import io.github.qlivedev.qlive.api.generic.DomainObject;

/**
 * Abstract base class for domain objects generated with qlive-graphql's
 * {@code io.github.qlivedev.qlive.graphql.jooq.DomainObjectGeneratorStrategy}. Adds a
 * JSON-ignored fetcher context to the domain object to optionally optimize relation fetching
 */
public abstract class GeneratedDomainObject
    implements DomainObject
{
    private FetcherContext fetcherContext;

    @Override
    public FetcherContext lookupFetcherContext()
    {
        return fetcherContext;
    }


    @Override
    public void provideFetcherContext(FetcherContext fetcherContext)
    {
        this.fetcherContext = fetcherContext;
    }
}
