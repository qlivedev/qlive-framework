package com.dataciders.qlive.runtime.merge;

import com.dataciders.qlive.runtime.QLiveException;
import de.quinscape.domainql.DomainQL;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// The ordered field names of one type, and the bit each of them owns in a field mask.
///
/// A field mask says which fields one write touched, by position. The position is not stable across
/// deployments -- adding a `flag` field to a type shifts every field from `f` on, and so does renaming a
/// relation, which involves no database change at all -- so a mask read under a different layout than it was
/// written under names a different set of fields, confidently and silently.
///
/// This is the layout a mask was written against, kept so that reading one back stays a matter of looking up
/// names rather than trusting positions. Its {@link #getId()} is a hash of the type name and the ordered
/// names, which is what lets a version record name its layout in one column and what makes storing a layout
/// an upsert with nothing to decide.
///
/// The names are what a mask is compared in. Turning a mask into names under the layout it was written with,
/// and then keeping only the names the type still has, is what "permute the mask into today's positions"
/// means -- and it drops the bits of fields that no longer exist, which is right: a field that is gone
/// cannot be in conflict.
public final class FieldLayout
{
    /// What a mask can hold, and therefore how many fields a versioned type may have. `numeric(39,0)` is
    /// 128 bits, 39 being `ceil(log10(2^128))`. A type with more fields is refused at startup rather than
    /// masked wrong.
    public final static int MAX_FIELDS = 128;

    /// Joins the names, both for the hash and for the `fields` column. A GraphQL name is
    /// `[_A-Za-z][_0-9A-Za-z]*`, so a comma cannot occur in one -- without a separator `["ab", "c"]` and
    /// `["a", "bc"]` would hash alike.
    public final static String SEPARATOR = ",";

    private final String typeName;

    private final List<String> fields;

    private final Map<String, Integer> indexes;

    private final String id;


    private FieldLayout(String typeName, List<String> fields)
    {
        if (fields.size() > MAX_FIELDS)
        {
            throw new QLiveException(
                "Type '" + typeName + "' has " + fields.size() + " fields, and a field mask holds " +
                    MAX_FIELDS + ". A versioned type cannot have more, since a field past the last bit " +
                    "would be silently absent from every mask instead of merging wrong."
            );
        }

        this.typeName = typeName;
        this.fields = List.copyOf(fields);

        final Map<String, Integer> byName = new HashMap<>();
        for (int i = 0; i < fields.size(); i++)
        {
            byName.put(fields.get(i), i);
        }
        this.indexes = Map.copyOf(byName);

        this.id = sha256(typeName + SEPARATOR + String.join(SEPARATOR, fields));
    }


    /// The layout of the given type as the schema in front of us has it: every field of the GraphQL type,
    /// alphabetically.
    ///
    /// Sorted here rather than taken in the order the schema happens to hand them over, because this is the
    /// order that assigns the bit indices and the order the hash is taken of. Those three have to be the
    /// same list or the hash certifies a layout nothing ever used.
    public static FieldLayout of(DomainQL domainQL, String typeName)
    {
        final GraphQLNamedType type = domainQL.getGraphQLSchema().getTypeMap().get(typeName);

        if (!(type instanceof GraphQLObjectType objectType))
        {
            throw new QLiveException("No object type '" + typeName + "' in the schema.");
        }

        final List<String> names = new ArrayList<>();
        for (GraphQLFieldDefinition field : objectType.getFieldDefinitions())
        {
            names.add(field.getName());
        }
        names.sort(String::compareTo);

        return of(typeName, names);
    }


    /// The layout of a type as it was, read back from a stored field list.
    public static FieldLayout of(String typeName, List<String> fields)
    {
        return new FieldLayout(typeName, fields);
    }


    /// SHA-256 of the type name and the ordered field names, in hex.
    ///
    /// The type name is hashed in even though the bit semantics do not need it: two types whose field lists
    /// happen to be identical would otherwise share one row, which makes the layout's own type meaningless
    /// and "what did this type look like then" unanswerable.
    public String getId()
    {
        return id;
    }


    public String getTypeName()
    {
        return typeName;
    }


    /// The field names in the order that assigns the bit indices.
    public List<String> getFields()
    {
        return fields;
    }


    /// The field names as the `fields` column holds them.
    public String getJoinedFields()
    {
        return String.join(SEPARATOR, fields);
    }


    /// The mask naming the given fields. A name this layout does not have is left out rather than refused:
    /// the callers are a change naming fields and a mask being read back, and neither is in a position to
    /// know what the layout has.
    public BigInteger mask(Iterable<String> fieldNames)
    {
        BigInteger mask = BigInteger.ZERO;

        for (String name : fieldNames)
        {
            final Integer index = indexes.get(name);

            if (index != null)
            {
                mask = mask.setBit(index);
            }
        }

        return mask;
    }


    /// The fields the given mask names, in this layout's order. Bits past the end of the field list are
    /// ignored -- they belong to fields that no longer exist, and a field that is gone cannot be in conflict.
    public Set<String> fields(BigInteger mask)
    {
        final Set<String> named = new LinkedHashSet<>();

        for (int i = 0; i < fields.size(); i++)
        {
            if (mask.testBit(i))
            {
                named.add(fields.get(i));
            }
        }

        return named;
    }


    @Override
    public boolean equals(Object o)
    {
        return o instanceof FieldLayout that && id.equals(that.id);
    }


    @Override
    public int hashCode()
    {
        return id.hashCode();
    }


    @Override
    public String toString()
    {
        return "FieldLayout " + id + " of '" + typeName + "': " + fields;
    }


    private static String sha256(String input)
    {
        final byte[] digest;

        try
        {
            digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        }
        catch (NoSuchAlgorithmException e)
        {
            // every JVM has it
            throw new QLiveException("No SHA-256 in this JVM", e);
        }

        final StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest)
        {
            hex.append(Character.forDigit((b >> 4) & 0xf, 16));
            hex.append(Character.forDigit(b & 0xf, 16));
        }

        return hex.toString();
    }
}
