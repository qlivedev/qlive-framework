package io.github.qlivedev.qlive.runtime.meta;

import io.github.qlivedev.qlive.graphql.OutputType;
import io.github.qlivedev.qlive.graphql.PojoTypes;
import io.github.qlivedev.qlive.graphql.QLiveDomain;
import io.github.qlivedev.qlive.graphql.meta.DomainMeta;
import io.github.qlivedev.qlive.graphql.meta.MetadataProvider;
import io.github.qlivedev.qlive.runtime.QLiveException;
import io.github.qlivedev.qlive.runtime.action.Invariants;
import io.github.qlivedev.qlive.runtime.action.Rule;
import io.github.qlivedev.qlive.runtime.action.RuleFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Attaches invariants to types: rules that hold for every write of the type's rows. See {@link Invariants}.
///
/// The way to give a generated type, whose POJO nobody writes, its invariants. Takes factory instances rather than
/// classes, so a rule class is constructed or written as a lambda:
///
///     RulesMetadataProvider.newProvider()
///         .rules(Quux.class, new NumsOrdered())
///         .rules(Quux.class, ctx -> new Rule(ctx.field("name").ne(value("")), "name", "Quux.nameRequired"))
///
/// A POJO the domain exposes can name its invariants itself, with `@Rules` on its class. The two add up.
public class RulesMetadataProvider
    implements MetadataProvider
{
    /// Factories by Java type, whose GraphQL name only the built domain knows.
    private final Map<Class<?>, List<RuleFactory>> byJavaType = new LinkedHashMap<>();

    /// Factories by GraphQL type name.
    private final Map<String, List<RuleFactory>> byTypeName = new LinkedHashMap<>();


    private RulesMetadataProvider()
    {
    }


    public static RulesMetadataProvider newProvider()
    {
        return new RulesMetadataProvider();
    }


    /// Attaches invariants to the type QLiveDomain exposes the given Java type as.
    ///
    /// @param javaType     the POJO class of the type
    /// @param factories    the rule factories
    public RulesMetadataProvider rules(Class<?> javaType, RuleFactory... factories)
    {
        PojoTypes.ensurePojoType(javaType);
        byJavaType.computeIfAbsent(javaType, ignored -> new ArrayList<>()).addAll(List.of(factories));
        return this;
    }


    /// Attaches invariants to the type of the given GraphQL name, for the types an application has no class at
    /// hand for.
    ///
    /// @param typeName     GraphQL type name
    /// @param factories    the rule factories
    public RulesMetadataProvider rules(String typeName, RuleFactory... factories)
    {
        byTypeName.computeIfAbsent(typeName, ignored -> new ArrayList<>()).addAll(List.of(factories));
        return this;
    }


    @Override
    public void provideMetaData(QLiveDomain domain, DomainMeta meta)
    {
        for (Map.Entry<Class<?>, List<RuleFactory>> declared : byJavaType.entrySet())
        {
            final OutputType outputType = domain.getTypeRegistry().lookup(declared.getKey());
            if (outputType == null)
            {
                throw new QLiveException(
                    "Invariants declared for " + declared.getKey() + ", which the domain does not expose as a type."
                );
            }
            write(domain, meta, outputType.getName(), declared.getValue());
        }

        for (Map.Entry<String, List<RuleFactory>> declared : byTypeName.entrySet())
        {
            if (domain.getTypeRegistry().lookup(declared.getKey()) == null)
            {
                throw new QLiveException(
                    "Invariants declared for type '" + declared.getKey() + "', which is no type of the domain."
                );
            }
            write(domain, meta, declared.getKey(), declared.getValue());
        }
    }


    private static void write(QLiveDomain domain, DomainMeta meta, String typeName, List<RuleFactory> factories)
    {
        final List<Rule> rules = new ArrayList<>(factories.size());
        for (RuleFactory factory : factories)
        {
            rules.add(Invariants.create(domain, typeName, factory));
        }
        Invariants.add(meta, typeName, rules);
    }
}
