package io.github.qlivedev.graphql.fetcher;

import org.svenson.AbstractDynamicProperties;

/**
 * Context object attached to a domain object to optimize subsequent reference fetchers.
 *
 * The reference fetcher ({@code io.github.qlivedev.graphql.fetcher.ReferenceFetcher} in qlive-graphql) will look for a fetcher context on its source domain object and if such a context exist will
 * return a property of the context which corresponds to the GraphQL field name for which the reference fetcher is registered.
 *
 * @see io.github.qlivedev.graphql.generic.DomainObject#provideFetcherContext(FetcherContext)
 */
public class FetcherContext
    extends AbstractDynamicProperties
{

}
