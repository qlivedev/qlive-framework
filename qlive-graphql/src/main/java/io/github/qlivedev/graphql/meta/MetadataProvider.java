package io.github.qlivedev.graphql.meta;

import io.github.qlivedev.graphql.DomainQL;

/**
 * Implemented by classes that want to contribute schema metadata to the DomainQL meta data.
 *
 * @see io.github.qlivedev.graphql.DomainQLBuilder#withMetadataProviders(MetadataProvider...) 
 */
public interface MetadataProvider
{
    void provideMetaData(DomainQL domainQL, DomainQLMeta meta);
}
