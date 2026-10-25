package io.github.qlivedev.qlive.runtime.layout;

import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import io.github.qlivedev.qlive.graphql.QLiveDomain;
import io.github.qlivedev.qlive.graphql.config.ManyToManyEnd;
import io.github.qlivedev.qlive.graphql.config.ManyToManyModel;
import io.github.qlivedev.qlive.graphql.config.RelationModel;
import io.github.qlivedev.qlive.model.condition.CNode;
import io.github.qlivedev.qlive.model.condition.ConditionVisitor;
import io.github.qlivedev.qlive.model.condition.FieldReference;
import io.github.qlivedev.qlive.model.layout.Layout;
import io.github.qlivedev.qlive.model.layout.LayoutComponentNode;
import io.github.qlivedev.qlive.model.layout.LayoutFieldNode;
import io.github.qlivedev.qlive.model.layout.LayoutNode;
import io.github.qlivedev.qlive.model.layout.LayoutSectionNode;
import io.github.qlivedev.qlive.model.layout.LayoutStatusNode;
import io.github.qlivedev.qlive.model.layout.Layouts;
import io.github.qlivedev.qlive.model.layout.Reaction;
import io.github.qlivedev.qlive.model.layout.ReactionEffect;
import io.github.qlivedev.qlive.model.layout.ReactionEvent;
import io.github.qlivedev.qlive.runtime.QLiveException;

import java.util.ArrayList;
import java.util.List;

/// Checks named layouts against the domain: what the frontend would only find when a page renders the form.
///
/// The domain type has to be an object type, and every field a form can edit: a field of that type, which is a
/// list only as a many-to-many field and an object only as a to-one relation over a single key. A field condition
/// goes with a to-one relation only, and reads fields of the form's row, through its to-one relations. The rules are
/// those of the frontend's `useField()`; what a widget, component or section name stands for is the frontend's
/// alone and isn't checked.
///
/// A reaction answers at least one event. The fields its events and effects name are fields a form can edit, by
/// the same rules, a mode is one there is, and its condition and values read the form's row as a field condition
/// does. Error classes aren't checked: a refusal of the application's own may carry any, as may a rule of an action
/// the layout doesn't name. Nor are effect names, which are the frontend's.
public final class LayoutCheck
{
    private final static List<String> MODES = List.of("normal", "disabled", "readOnly", "text");

    private final QLiveDomain domain;


    public LayoutCheck(QLiveDomain domain)
    {
        this.domain = domain;
    }


    /// Checks named layouts.
    ///
    /// @throws QLiveException listing every problem of every layout
    public void check(Layouts layouts)
    {
        final List<String> problems = new ArrayList<>();
        layouts.asMap().forEach((name, layout) -> problems.addAll(problems(name, layout)));
        if (!problems.isEmpty())
        {
            throw new QLiveException("Invalid layouts:\n  " + String.join("\n  ", problems));
        }
    }


    /// What is wrong with the layout, one line each; empty for a layout whose references all hold.
    ///
    /// @param name     what the layout is called, for the messages
    public List<String> problems(String name, Layout layout)
    {
        final List<String> problems = new ArrayList<>();
        final String type = layout.getDomainType();
        if (!(domain.getGraphQLSchema().getType(type) instanceof GraphQLObjectType))
        {
            problems.add("Layout " + name + ": " + type + " is no domain type.");
            return problems;
        }
        nodes(name, type, layout.getNodes(), problems);
        if (layout.getReactions() != null)
        {
            reactions(name, type, layout.getReactions(), problems);
        }
        return problems;
    }


    private void nodes(String name, String type, List<LayoutNode> nodes, List<String> problems)
    {
        for (LayoutNode node : nodes)
        {
            switch (node)
            {
                case LayoutFieldNode field -> field(name, type, field, problems);
                case LayoutSectionNode section -> nodes(name, type, section.getNodes(), problems);
                case LayoutComponentNode component ->
                {
                    if (component.getNodes() != null)
                    {
                        nodes(name, type, component.getNodes(), problems);
                    }
                }
                case LayoutStatusNode ignored ->
                {
                }
            }
        }
    }


    private void field(String name, String type, LayoutFieldNode node, List<String> problems)
    {
        final String path = node.getField();
        final String where = "Layout " + name + ", field " + path + " of " + type + ": ";

        if (node.getMode() != null && !MODES.contains(node.getMode()))
        {
            problems.add(where + "no mode " + node.getMode() + ", only " + String.join(", ", MODES) + ".");
        }

        final String problem = fieldProblem(type, path);
        if (problem != null)
        {
            problems.add(where + problem);
            return;
        }

        if (node.getCondition() != null)
        {
            if (!isToOne(type, path))
            {
                problems.add(where + "a condition narrows the rows a to-one relation offers, and this is none.");
                return;
            }
            readProblems(type, node.getCondition(), where + "the condition", problems);
        }
    }


    private void reactions(String name, String type, List<Reaction> reactions, List<String> problems)
    {
        for (int i = 0; i < reactions.size(); i++)
        {
            final Reaction reaction = reactions.get(i);
            final String where = "Layout " + name + ", reaction " + i;

            if (reaction.getOn().isEmpty())
            {
                problems.add(where + ": answers no event.");
            }
            for (ReactionEvent event : reaction.getOn())
            {
                final String path = switch (event)
                {
                    case ReactionEvent.Change change -> change.getChange();
                    case ReactionEvent.Blur blur -> blur.getBlur();
                    case ReactionEvent.ErrorAt errorAt -> errorAt.getErrorAt();
                    case ReactionEvent.Init ignored -> null;
                    case ReactionEvent.ErrorClass ignored -> null;
                };
                final String problem = path == null ? null : fieldProblem(type, path);
                if (problem != null)
                {
                    problems.add(where + ", event on " + path + " of " + type + ": " + problem);
                }
            }
            if (reaction.getWhen() != null)
            {
                readProblems(type, reaction.getWhen(), where + ": the condition", problems);
            }
            effects(type, reaction.getEffects(), where, problems);
            if (reaction.getOtherwise() != null)
            {
                effects(type, reaction.getOtherwise(), where, problems);
            }
        }
    }


    private void effects(String type, List<ReactionEffect> effects, String where, List<String> problems)
    {
        for (ReactionEffect effect : effects)
        {
            final String path = effect.getField();
            final String at = where + ", effect on " + path + " of " + type + ": ";
            final String problem = fieldProblem(type, path);
            if (problem != null)
            {
                problems.add(at + problem);
                continue;
            }
            switch (effect)
            {
                case ReactionEffect.Mode mode ->
                {
                    if (!MODES.contains(mode.getMode()))
                    {
                        problems.add(at + "no mode " + mode.getMode() + ", only " + String.join(", ", MODES) + ".");
                    }
                }
                case ReactionEffect.Expression expression ->
                    readProblems(type, expression.getValue(), at + "the value", problems);
                case ReactionEffect.FunctionReference ignored ->
                {
                }
            }
        }
    }


    /// What is wrong with a field path for a form, null for one it can edit: a field of the type, which is a list only
    /// as a many-to-many field and an object only as a to-one relation over a single key.
    private String fieldProblem(String type, String path)
    {
        if (path.startsWith("input."))
        {
            return "action input isn't supported yet.";
        }
        if (path.contains("."))
        {
            return "a form edits its own row's fields; a related row's are a field group's.";
        }
        final GraphQLFieldDefinition definition = objectType(type).getFieldDefinition(path);
        if (definition == null)
        {
            return "no such field.";
        }

        final GraphQLType fieldType = GraphQLTypeUtil.unwrapNonNull(definition.getType());
        if (GraphQLTypeUtil.isList(fieldType))
        {
            return isManyToMany(type, path) ? null : "a list, and not a many-to-many field.";
        }
        if (GraphQLTypeUtil.unwrapAll(fieldType) instanceof GraphQLObjectType)
        {
            final RelationModel relation = toOneRelation(type, path);
            if (relation == null)
            {
                return "an object, and not a to-one relation.";
            }
            if (relation.getSourceFields().size() != 1)
            {
                return "a relation over a key of several fields, which a form can't edit yet.";
            }
        }
        return null;
    }


    /// Whether a field a form can edit is a to-one relation.
    private boolean isToOne(String type, String path)
    {
        return !GraphQLTypeUtil.isList(GraphQLTypeUtil.unwrapNonNull(objectType(type).getFieldDefinition(path).getType())) &&
            toOneRelation(type, path) != null;
    }


    /// The field references of a condition or value the form's row can't be read along.
    ///
    /// @param what     what reads them, for the messages
    private void readProblems(String type, CNode node, String what, List<String> problems)
    {
        node.accept(new ConditionVisitor<Void>()
        {
            @Override
            public Object visit(FieldReference field, Void data)
            {
                final String problem = conditionPathProblem(type, field.getName());
                if (problem != null)
                {
                    problems.add(what + " reads " + field.getName() + ", " + problem);
                }
                return null;
            }
        }, null);
    }


    /// Why a condition can't read the path on the form's row, null where it can: every step but the last a to-one
    /// relation, the last a field of the type reached.
    private String conditionPathProblem(String type, String path)
    {
        final String[] steps = path.split("\\.");
        String current = type;
        for (int i = 0; i < steps.length - 1; i++)
        {
            final RelationModel relation = toOneRelation(current, steps[i]);
            if (relation == null)
            {
                return "but " + steps[i] + " is no to-one relation of " + current + ".";
            }
            current = relation.getTargetType();
        }
        final String last = steps[steps.length - 1];
        return objectType(current).getFieldDefinition(last) == null
            ? "but " + current + " has no field " + last + "."
            : null;
    }


    private GraphQLObjectType objectType(String type)
    {
        return (GraphQLObjectType) domain.getGraphQLSchema().getType(type);
    }


    private RelationModel toOneRelation(String type, String objectName)
    {
        for (RelationModel relation : domain.getMetaData().getRelationModels())
        {
            if (relation.getSourceType().equals(type) && objectName.equals(relation.getLeftSideObjectName()))
            {
                return relation;
            }
        }
        return null;
    }


    private boolean isManyToMany(String type, String field)
    {
        for (ManyToManyModel model : domain.getMetaData().getManyToManyModels())
        {
            for (ManyToManyEnd end : List.of(model.getLeft(), model.getRight()))
            {
                if (end.getType().equals(type) && field.equals(end.getField()))
                {
                    return true;
                }
            }
        }
        return false;
    }
}
