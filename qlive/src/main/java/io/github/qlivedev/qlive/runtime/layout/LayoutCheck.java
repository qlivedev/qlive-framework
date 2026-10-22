package io.github.qlivedev.qlive.runtime.layout;

import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import io.github.qlivedev.qlive.graphql.QLiveDomain;
import io.github.qlivedev.qlive.graphql.config.ManyToManyEnd;
import io.github.qlivedev.qlive.graphql.config.ManyToManyModel;
import io.github.qlivedev.qlive.graphql.config.RelationModel;
import io.github.qlivedev.qlive.model.condition.ConditionVisitor;
import io.github.qlivedev.qlive.model.condition.FieldReference;
import io.github.qlivedev.qlive.model.layout.Layout;
import io.github.qlivedev.qlive.model.layout.LayoutComponentNode;
import io.github.qlivedev.qlive.model.layout.LayoutFieldNode;
import io.github.qlivedev.qlive.model.layout.LayoutNode;
import io.github.qlivedev.qlive.model.layout.LayoutSectionNode;
import io.github.qlivedev.qlive.model.layout.LayoutStatusNode;
import io.github.qlivedev.qlive.model.layout.Layouts;
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

        if (path.startsWith("input."))
        {
            problems.add(where + "action input isn't supported yet.");
            return;
        }
        if (path.contains("."))
        {
            problems.add(where + "a form edits its own row's fields; a related row's are a field group's.");
            return;
        }
        final GraphQLFieldDefinition definition = objectType(type).getFieldDefinition(path);
        if (definition == null)
        {
            problems.add(where + "no such field.");
            return;
        }

        final GraphQLType fieldType = GraphQLTypeUtil.unwrapNonNull(definition.getType());
        boolean toOne = false;
        if (GraphQLTypeUtil.isList(fieldType))
        {
            if (!isManyToMany(type, path))
            {
                problems.add(where + "a list, and not a many-to-many field.");
            }
        }
        else if (GraphQLTypeUtil.unwrapAll(fieldType) instanceof GraphQLObjectType)
        {
            final RelationModel relation = toOneRelation(type, path);
            if (relation == null)
            {
                problems.add(where + "an object, and not a to-one relation.");
            }
            else if (relation.getSourceFields().size() != 1)
            {
                problems.add(where + "a relation over a key of several fields, which a form can't edit yet.");
            }
            else
            {
                toOne = true;
            }
        }

        if (node.getCondition() != null)
        {
            if (!toOne)
            {
                problems.add(where + "a condition narrows the rows a to-one relation offers, and this is none.");
                return;
            }
            node.getCondition().accept(new ConditionVisitor<Void>()
            {
                @Override
                public Object visit(FieldReference field, Void data)
                {
                    final String problem = conditionPathProblem(type, field.getName());
                    if (problem != null)
                    {
                        problems.add(where + "the condition reads " + field.getName() + ", " + problem);
                    }
                    return null;
                }
            }, null);
        }
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
