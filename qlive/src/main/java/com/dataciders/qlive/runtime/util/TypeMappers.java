package com.dataciders.qlive.runtime.util;

import org.svenson.ClassNameBasedTypeMapper;
import org.svenson.TypeMapper;
import org.svenson.matcher.SubtypeMatcher;
import org.svenson.tokenize.JSONTokenizer;

import java.util.List;

/// The Svenson type-mapper plumbing QLive's own JSON hierarchies are parsed with.
///
/// Both of QLive's discriminated hierarchies -- the FilterDSL's condition nodes and the push message kinds
/// -- are a closed set of subclasses under one base class in one package, named on the wire by their own
/// simple class name. That is one setup, described once here, rather than a copy per parser that can drift
/// from the other.
public final class TypeMappers
{
    private TypeMappers()
    {
        // no instances
    }


    /// A mapper that picks the subclass of `baseType` whose simple name the JSON's "type" field names.
    ///
    /// Scoped to the base type in both directions: it only fires where the parser already expects one of
    /// these, and it refuses to build anything that is not one. A "type" naming a class outside the
    /// hierarchy is an error, not a way into arbitrary instantiation -- messages arrive from browsers.
    public static ClassNameBasedTypeMapper byClassName(Class<?> baseType)
    {
        final ClassNameBasedTypeMapper typeMapper = new ClassNameBasedTypeMapper();
        typeMapper.setBasePackage(baseType.getPackage().getName());
        typeMapper.setEnforcedBaseType(baseType);
        typeMapper.setDiscriminatorField("type");
        typeMapper.setPathMatcher(new SubtypeMatcher(baseType));
        return typeMapper;
    }


    /// Consults the given mappers in turn and takes the first one that actually answers. What lets one
    /// parser dispatch two independent hierarchies -- a push message kind, and a FilterDSL node nested
    /// inside it -- each mapper scoped to its own base type.
    ///
    /// This is what Svenson's own `CompositeTypeMapper` looks like it does, and cannot: it treats the
    /// first non-null result as final, while `AbstractPropertyValueBasedTypeMapper` -- the base of every
    /// class-name and property-value mapper -- returns the *incoming* hint unchanged when its own path
    /// matcher does not match. The first mapper in the list therefore always looks like it answered,
    /// whatever it was asked about, and the rest never run. Telling the two apart is the whole of the
    /// difference: a mapper that hands back the hint it was given has declined, and the next one gets its
    /// turn.
    public static TypeMapper firstAnswer(TypeMapper... mappers)
    {
        return new FirstAnswer(List.of(mappers));
    }


    private record FirstAnswer(
        List<TypeMapper> mappers
    )
        implements TypeMapper
    {
        @Override
        public Class<?> getTypeHint(JSONTokenizer tokenizer, String parsePathInfo, Class typeHint)
        {
            for (TypeMapper mapper : mappers)
            {
                final Class<?> answer = mapper.getTypeHint(tokenizer, parsePathInfo, typeHint);

                if (answer != null && answer != typeHint)
                {
                    return answer;
                }
            }

            return typeHint;
        }
    }
}
