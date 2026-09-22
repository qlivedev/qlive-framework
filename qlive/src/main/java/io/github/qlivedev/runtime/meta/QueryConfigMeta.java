package io.github.qlivedev.runtime.meta;

import io.github.qlivedev.runtime.util.Util;
import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.graphql.GenericTypeReference;
import io.github.qlivedev.graphql.OutputType;
import io.github.qlivedev.graphql.meta.DomainQLTypeMeta;

import java.util.Map;
import java.util.Optional;

/// The type meta data entries saying what a query config of a type starts out as and how far it may go, and
/// how they are read back.
///
/// A type's query config delta is the application's answer to "what does querying this look like when
/// nothing says otherwise" -- a page size, a sort order, a condition every query of that type carries. It is
/// a *delta*, the same partial config the client's QueryConfigDelta is, so a type naming a page size and
/// nothing else leaves the rest at the defaults {@link io.github.qlivedev.model.QueryConfig} describes.
///
/// It hangs on the *row* type rather than on the query document type it is reached through: an application
/// declares this on the domain type it owns, and every query returning a document of those rows gets it,
/// whatever the degenerified document type ends up being called.
///
/// Beside it sits the maximum page size, which is the opposite kind of statement: not what a query starts
/// out as but what it is held to, whoever asks and however the config got here. See
/// {@link #maxPageSizeForType(QLiveDomain, Class)}.
///
/// Both are written with {@link QueryConfigMetadataProvider}, which an application registers as a bean if it
/// wants any of this -- nothing here happens to an application that declares nothing. The delta is read on
/// the way into an injection, see {@link io.github.qlivedev.runtime.service.QueryConfigArgumentProcessor},
/// the maximum where a query is executed, see
/// {@link io.github.qlivedev.runtime.query.DefaultQueryDocumentService}. Both are readable by the client,
/// which is sent the same meta data.
public final class QueryConfigMeta
{
    private QueryConfigMeta()
    {
        // no instances
    }

    /// Name of the type meta data property holding the delta. Has to agree with `DomainQLTypeMetaProps` on
    /// the client, which declares the same name to TypeScript.
    public final static String QUERY_CONFIG = "queryConfig";

    /// Name of the type meta data property holding the maximum page size. Has to agree with
    /// `DomainQLTypeMetaProps` on the client, which declares the same name to TypeScript.
    public final static String MAX_PAGE_SIZE = "maxPageSize";

    /// The delta declared for the row type of the given query document type.
    ///
    /// @param documentTypeName  name of a degenerified QueryDocument type, e.g. "FooDocument"
    ///
    /// @return the delta, or `null` where that is no query document type or its row type declares none
    public static Map<String, Object> deltaForDocumentType(QLiveDomain domainQL, String documentTypeName)
    {
        final Optional<GenericTypeReference> documentType = Util.findQueryDocumentType(
            domainQL, documentTypeName
        );

        return documentType
            .map(reference -> deltaForType(domainQL, domainQL.getTypeRegistry().lookup(reference.getTypeParameters().getFirst()).getJavaType()))
            .orElse(null);
    }


    /// The delta declared for the given type.
    ///
    /// @param javaType  a Java type, exposed by the domain or not
    ///
    /// @return the delta, or `null` where the type is unknown or declares none
    public static Map<String, Object> deltaForType(QLiveDomain domainQL, Class<?> javaType)
    {
        final DomainQLTypeMeta typeMeta = Util.typeMeta(domainQL, javaType.getSimpleName());

        return typeMeta == null ? null : typeMeta.getMeta(QUERY_CONFIG);
    }


    /// The maximum page size declared for the given type.
    ///
    /// @param javaType  a Java type, exposed by the domain or not
    ///
    /// @return the maximum, or 0 where the type is unknown or declares none. 0 is also what a query config
    ///         says when it wants every row, so "no maximum" and "no limit" are the same number throughout.
    public static int maxPageSizeForType(QLiveDomain domainQL, Class<?> javaType)
    {
        final OutputType outputType = domainQL.getTypeRegistry().lookup(javaType);
        if (outputType == null)
        {
            return 0;
        }
        else
        {
            final String typeName = outputType.getName();
            final DomainQLTypeMeta typeMeta = Util.typeMeta(domainQL, typeName);

            final Object maxPageSize = typeMeta == null ? null : typeMeta.getMeta(MAX_PAGE_SIZE);

            return maxPageSize instanceof Number number ? number.intValue() : 0;
        }
    }
}
