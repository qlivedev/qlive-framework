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

/// What a {@link RuleFactory} builds its rule with: field references into the action's parameters, each checked
/// when it is asked for.
///
/// Every path starts with a parameter name. Into a subject it continues with the stored fields of its type,
/// relations included, as a query document's paths do: `bar.owner.login`. Into input it continues with the
/// properties of the parameter's Java type, positionally: `bazs.0.name`. A path naming no parameter, or no
/// field, fails right here, at startup, naming the action and the rule class.
public final class RuleContext
{
    private final QLiveDomain domain;

    private final Action action;

    private final Class<?> ruleClass;


    RuleContext(QLiveDomain domain, Action action, Class<?> ruleClass)
    {
        this.domain = domain;
        this.action = action;
        this.ruleClass = ruleClass;
    }


    /// The name of the action the rule is created for.
    public String getActionName()
    {
        return action.getName();
    }


    /// A reference to a field of the action's parameters.
    ///
    /// @param path     dotted path starting with a parameter name
    ///
    /// @return field reference to build the condition with
    public FieldReference field(String path)
    {
        check(path);
        return new FieldReference(path);
    }


    /// Checks a path against the action's parameters.
    void check(String path)
    {
        if (path == null || path.isEmpty())
        {
            throw fail("A rule path can't be empty");
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
        return new QLiveException(action + ", rule " + ruleClass.getName() + ": " + message);
    }
}
