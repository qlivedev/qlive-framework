package io.github.qlivedev.qlive.runtime.action;

import io.github.qlivedev.qlive.graphql.QLiveDomain;
import io.github.qlivedev.qlive.graphql.meta.DomainMeta;
import io.github.qlivedev.qlive.graphql.meta.DomainTypeMeta;
import io.github.qlivedev.qlive.model.condition.CNode;
import io.github.qlivedev.qlive.model.condition.Component;
import io.github.qlivedev.qlive.model.condition.Condition;
import io.github.qlivedev.qlive.model.condition.FieldReference;
import io.github.qlivedev.qlive.model.condition.Operation;
import io.github.qlivedev.qlive.runtime.QLiveException;
import io.github.qlivedev.qlive.runtime.filter.FilterTransformer;
import io.github.qlivedev.qlive.runtime.util.Util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/// The invariants of the domain's types: rules that hold for every write of a type's rows, whoever writes.
///
/// An invariant is a {@link Rule} like an action's, created by a {@link RuleFactory} with a context rooted at the
/// type, so that its paths are the type's own fields: `end`, `start`. It reads one row and nothing else, and is
/// evaluated in memory, on the row as a write sends it.
///
/// They are named in two places that add up: `@Rules` on the class of a POJO the domain exposes, and a
/// {@link io.github.qlivedev.qlive.runtime.meta.RulesMetadataProvider} the application registers for any type,
/// the generated ones included. Both land in the type meta data under {@link #RULES}, which is where the merge
/// reads them and how they reach the client, in the bootstrap.
public final class Invariants
{
    /// Key of the type meta data the invariants of a type are held under, as a list of {@link Rule}s.
    public final static String RULES = "rules";


    private Invariants()
    {
        // no instances
    }


    /// Creates the invariant a factory builds for a type, and checks every path it reads.
    ///
    /// @param domain       the domain
    /// @param typeName     GraphQL name of the type
    /// @param factory      the rule factory
    ///
    /// @return the invariant
    public static Rule create(QLiveDomain domain, String typeName, RuleFactory factory)
    {
        if (factory == null)
        {
            throw new QLiveException("Type '" + typeName + "': no rule factory given");
        }

        final RuleContext ctx = RuleContext.forType(domain, typeName, factory.getClass());
        final Rule rule = factory.create(ctx);
        if (rule == null)
        {
            throw ctx.fail("The rule factory returned no rule");
        }

        if (rule.getField() != null)
        {
            ctx.check(rule.getField());
        }

        // the factory may have built paths without the context, so every one of them is checked again
        final Set<String> paths = new LinkedHashSet<>();
        collectPaths(rule.getCondition(), paths);
        for (String path : paths)
        {
            ctx.check(path);
        }
        return rule;
    }


    /// Instantiates a rule factory named by its class.
    ///
    /// @param ruleClass    the class named
    /// @param owner        what names it, for messages: an action or a type
    ///
    /// @return the factory
    public static RuleFactory instantiate(Class<?> ruleClass, String owner)
    {
        if (!RuleFactory.class.isAssignableFrom(ruleClass))
        {
            throw new QLiveException(owner + ": @Rules names " + ruleClass.getName() + ", which is no " +
                RuleFactory.class.getSimpleName());
        }

        try
        {
            return (RuleFactory) ruleClass.getConstructor().newInstance();
        }
        catch (ReflectiveOperationException e)
        {
            throw new QLiveException(owner + ": Cannot instantiate rule " + ruleClass.getName() + ". A rule " +
                "factory is public, top-level or static nested, with a public no-arg constructor.", e);
        }
    }


    /// Adds invariants to a type's meta data, after those another provider wrote.
    ///
    /// @param meta         the domain's meta data, as a provider is handed it
    /// @param typeName     GraphQL name of the type
    /// @param rules        the invariants to add
    public static void add(DomainMeta meta, String typeName, Collection<Rule> rules)
    {
        if (rules.isEmpty())
        {
            return;
        }

        final DomainTypeMeta typeMeta = meta.getTypeMeta(typeName);
        final List<Rule> existing = typeMeta.getMeta(RULES);

        final List<Rule> all = new ArrayList<>(existing != null ? existing : List.of());
        all.addAll(rules);
        typeMeta.setMeta(RULES, List.copyOf(all));
    }


    /// The invariants of the given type, empty where it has none or is no type of the domain.
    ///
    /// @param domain       the domain
    /// @param typeName     GraphQL type name, known or not
    ///
    /// @return the invariants, in the order they were named
    public static List<Rule> of(QLiveDomain domain, String typeName)
    {
        final DomainTypeMeta typeMeta = Util.typeMeta(domain, typeName);
        final List<Rule> rules = typeMeta == null ? null : typeMeta.getMeta(RULES);
        return rules != null ? rules : List.of();
    }


    /// Every message key of every invariant of the domain, which the translations offer whether the frontend
    /// names them or not: they arrive with a refusal, and the client evaluates them itself.
    ///
    /// @param domain   the domain
    ///
    /// @return the message keys
    public static Set<String> messageKeys(QLiveDomain domain)
    {
        final Set<String> keys = new LinkedHashSet<>();
        for (String typeName : domain.getGraphQLSchema().getTypeMap().keySet())
        {
            for (Rule rule : of(domain, typeName))
            {
                keys.add(rule.getMessageKey());
            }
        }
        return keys;
    }


    /// The fields an invariant reads.
    ///
    /// @param rule     the invariant
    ///
    /// @return field names
    public static Set<String> fields(Rule rule)
    {
        final Set<String> paths = new LinkedHashSet<>();
        collectPaths(rule.getCondition(), paths);
        return paths;
    }


    /// Whether an invariant holds for a row.
    ///
    /// @param rule     the invariant
    /// @param row      the row's values by field name, as the write sends them
    ///
    /// @return `true` where it holds
    public static boolean holds(Rule rule, Map<String, Object> row)
    {
        final Predicate<Object> predicate = new FilterTransformer(null).transform(rule.getCondition());
        return predicate == null || predicate.test(row);
    }


    /// Collects the field paths a condition reads.
    static void collectPaths(CNode node, Set<String> paths)
    {
        switch (node)
        {
            case null -> { }
            case FieldReference ref -> paths.add(ref.getName());
            case Condition c -> nullSafe(c.getOperands()).forEach(operand -> collectPaths(operand, paths));
            case Operation o -> nullSafe(o.getOperands()).forEach(operand -> collectPaths(operand, paths));
            case Component component -> collectPaths(component.getCondition(), paths);
            default -> { }
        }
    }


    private static <T> List<T> nullSafe(List<T> list)
    {
        return list == null ? List.of() : list;
    }
}
