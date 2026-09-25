package io.github.qlivedev.graphql.meta;

import io.github.qlivedev.graphql.QLiveDomain;

/**
 * Implemented by classes that want to contribute schema metadata to the domain's meta data.
 *
 * @see io.github.qlivedev.graphql.QLiveDomainBuilder#withMetadataProviders(MetadataProvider...) 
 */
public interface MetadataProvider
{
    void provideMetaData(QLiveDomain domainQL, DomainQLMeta meta);
}
