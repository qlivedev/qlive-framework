package io.github.qlivedev.qlive.graphql.meta;

import io.github.qlivedev.qlive.graphql.QLiveDomain;

/**
 * Implemented by classes that want to contribute schema metadata to the domain's meta data.
 *
 * @see io.github.qlivedev.qlive.graphql.QLiveDomainBuilder#withMetadataProviders(MetadataProvider...) 
 */
public interface MetadataProvider
{
    void provideMetaData(QLiveDomain domain, DomainMeta meta);
}
