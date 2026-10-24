package io.github.qlivedev.qlive.runtime.action;

import io.github.qlivedev.qlive.graphql.QLiveDomain;
import io.github.qlivedev.qlive.graphql.logic.Action;
import io.github.qlivedev.qlive.graphql.logic.ActionSubject;
import io.github.qlivedev.qlive.model.condition.FieldReference;
import io.github.qlivedev.qlive.runtime.QLiveException;
import io.github.qlivedev.qlive.runtime.filter.PropertyPath;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/// What a {@link RuleFactory} builds its rule with: field references, each checked when it is asked for.
///
/// For an action, every path starts with a parameter name. Into a subject it continues with the stored fields of
/// its type, relations included, as a query document's paths do: `bar.owner.login`. Into input it continues with
/// the properties of the parameter's Java type, positionally: `bazs.0.name`.
///
/// For a type, the rule is an invariant and a path is one of the type's own fields: `end`. A relation field is
/// none of them, so an invariant reads one row and nothing else; a foreign key is a field of its own.
///
/// A path naming no parameter, or no field, fails right here, at startup, naming the action or the type and the
/// rule.
public final class RuleContext
{
    private final QLiveDomain domain;

    private final Action action;

    private final String typeName;

    private final Class<?> ruleClass;


    RuleContext(QLiveDomain domain, Action action, Class<?> ruleClass)
    {
        this.domain = domain;
        this.action = action;
        this.typeName = null;
        this.ruleClass = ruleClass;
    }


    private RuleContext(QLiveDomain domain, String typeName, Class<?> ruleClass)
    {
        this.domain = domain;
        this.action = null;
        this.typeName = typeName;
        this.ruleClass = ruleClass;
    }


    /// The context of an invariant of the given type.
    ///
    /// @param domain       the domain
    /// @param typeName     GraphQL name of the type
    /// @param ruleClass    class of the factory, for messages
    ///
    /// @return context
    public static RuleContext forType(QLiveDomain domain, String typeName, Class<?> ruleClass)
    {
        return new RuleContext(domain, typeName, ruleClass);
    }


    /// The name of the action the rule is created for, `null` for an invariant.
    public String getActionName()
    {
        return action != null ? action.getName() : null;
    }


    /// The name of the type the invariant is created for, `null` for an action's rule.
    public String getTypeName()
    {
        return typeName;
    }


    /// A reference to a field of the action's parameters, or of the type.
    ///
    /// @param path     dotted path starting with a parameter name, or a field of the type
    ///
    /// @return field reference to build the condition with
    public FieldReference field(String path)
    {
        check(path);
        return new FieldReference(path);
    }


    /// Checks a path against the action's parameters, or against the type's own fields.
    void check(String path)
    {
        if (path == null || path.isEmpty())
        {
            throw fail("A rule path can't be empty");
        }

        if (typeName != null)
        {
            checkOwnField(path);
            return;
        }

        final List<String> segments = Arrays.asList(path.split("\\.", -1));
        final String parameterName = segments.get(0);
        final int index = action.getParameterNames().indexOf(parameterName);
        if (index < 0)
        {
            throw fail("Path '" + path + "' names no parameter. The parameters are " +
                action.getParameterNames().stream().filter(Objects::nonNull).toList());
        }

        final ActionSubject subject = subject(parameterName);
        if (subject != null)
        {
            checkStored(path, subject, segments.subList(1, segments.size()));
        }
        else if (segments.size() > 1)
        {
            try
            {
                PropertyPath.compile(
                    String.join(".", segments.subList(1, segments.size())),
                    action.getParameters()[index].getType()
                );
            }
            catch (QLiveException e)
            {
                throw fail("Path '" + path + "' names no property of input '" + parameterName + "': " + e.getMessage());
            }
        }
    }


    private void checkStored(String path, ActionSubject subject, List<String> fields)
    {
        if (fields.isEmpty())
        {
            throw fail("Path '" + path + "' names the subject itself; a rule reads its fields");
        }

        GraphQLType type = domain.getGraphQLSchema().getType(
            domain.getTypeRegistry().lookup(subject.pojoType()).getName()
        );
        for (String field : fields)
        {
            if (!(type instanceof GraphQLObjectType objectType))
            {
                throw fail("Path '" + path + "' continues past a field that is no object: '" + field + "'");
            }

            final GraphQLFieldDefinition definition = objectType.getFieldDefinition(field);
            if (definition == null)
            {
                throw fail("Path '" + path + "': type '" + objectType.getName() + "' has no field '" + field + "'");
            }
            type = GraphQLTypeUtil.unwrapAll(definition.getType());
        }
    }


    /// An invariant's path: a field of the type that is no relation.
    private void checkOwnField(String path)
    {
        final GraphQLObjectType objectType = (GraphQLObjectType) domain.getGraphQLSchema().getType(typeName);
        final GraphQLFieldDefinition definition = objectType.getFieldDefinition(path);
        if (definition == null)
        {
            throw fail("Path '" + path + "' names no field of '" + typeName + "'. An invariant reads the type's own " +
                "fields, without a parameter name before them.");
        }
        if (GraphQLTypeUtil.unwrapAll(definition.getType()) instanceof GraphQLObjectType)
        {
            throw fail("Path '" + path + "' names a relation. An invariant reads the type's own fields alone; " +
                "a rule over related rows belongs to an action.");
        }
    }


    private ActionSubject subject(String name)
    {
        for (ActionSubject subject : action.getSubjects())
        {
            if (subject.name().equals(name))
            {
                return subject;
            }
        }
        return null;
    }


    QLiveException fail(String message)
    {
        return new QLiveException(
            (action != null ? action.toString() : "Type '" + typeName + "'") + ", rule " + ruleClass.getName() +
                ": " + message
        );
    }
}
