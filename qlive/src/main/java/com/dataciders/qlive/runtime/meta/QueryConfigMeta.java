package com.dataciders.qlive.runtime.meta;

import com.dataciders.qlive.runtime.util.Util;
import de.quinscape.domainql.DomainQL;
import de.quinscape.domainql.GenericTypeReference;
import de.quinscape.domainql.meta.DomainQLTypeMeta;

import java.util.Map;
import java.util.Optional;

/// The type meta data entry saying what a query config of a type starts out as, and how it is read back.
///
/// A type's query config delta is the application's answer to "what does querying this look like when
/// nothing says otherwise" -- a page size, a sort order, a condition every query of that type carries. It is
/// a *delta*, the same partial config the client's QueryConfigDelta is, so a type naming a page size and
/// nothing else leaves the rest at the defaults {@link com.dataciders.qlive.model.QueryConfig} describes.
///
/// It hangs on the *row* type rather than on the query document type it is reached through: an application
/// declares this on the domain type it owns, and every query returning a document of those rows gets it,
/// whatever the degenerified document type ends up being called.
///
/// Written with {@link QueryConfigMetadataProvider}, which an application registers as a bean if it wants
/// this at all -- nothing here happens to an application that declares nothing. Read on the way into an
/// injection, see {@link com.dataciders.qlive.runtime.service.QueryConfigArgumentProcessor}, and readable by
/// the client, which is sent the same meta data.
public final class QueryConfigMeta
{
    private QueryConfigMeta()
    {
        // no instances
    }

    /// Name of the type meta data property holding the delta. Has to agree with `DomainQLTypeMetaProps` on
    /// the client, which declares the same name to TypeScript.
    public final static String QUERY_CONFIG = "queryConfig";

    /// Key the DomainQL meta data holds its type meta data under. Not a constant of DomainQLMeta's own,
    /// which only names its addenda -- taken from there because {@link
    /// de.quinscape.domainql.meta.DomainQLMeta#getTypeMeta(String)} answers an unknown type with an
    /// exception, and asking about one is exactly what this does.
    private final static String TYPES = "types";


    /// The delta declared for the row type of the given query document type.
    ///
    /// @param documentTypeName  name of a degenerified QueryDocument type, e.g. "FooDocument"
    ///
    /// @return the delta, or `null` where that is no query document type or its row type declares none
    public static Map<String, Object> deltaForDocumentType(DomainQL domainQL, String documentTypeName)
    {
        final Optional<GenericTypeReference> documentType = Util.findQueryDocumentType(
            domainQL, documentTypeName
        );

        return documentType
            .map(reference -> deltaForType(domainQL, reference.getTypeParameters().getFirst()))
            .orElse(null);
    }


    /// The delta declared for the given type.
    ///
    /// @param typeName  name of a GraphQL type, known or not
    ///
    /// @return the delta, or `null` where the type is unknown or declares none
    @SuppressWarnings("unchecked")
    public static Map<String, Object> deltaForType(DomainQL domainQL, String typeName)
    {
        final Map<String, DomainQLTypeMeta> types =
            (Map<String, DomainQLTypeMeta>) domainQL.getMetaData().getData().get(TYPES);

        final DomainQLTypeMeta typeMeta = types.get(typeName);

        return typeMeta == null ? null : typeMeta.getMeta(QUERY_CONFIG);
    }
}
