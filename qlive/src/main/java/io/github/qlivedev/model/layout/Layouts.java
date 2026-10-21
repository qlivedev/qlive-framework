package io.github.qlivedev.model.layout;

import io.github.qlivedev.graphql.util.JSONHolder;
import io.github.qlivedev.model.condition.ConditionParser;
import io.github.qlivedev.runtime.QLiveException;
import io.github.qlivedev.util.JSONUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Named layouts to and from the JSON the frontend reads: a map of layout name to layout.
///
/// What a `LayoutService` returns. One reading files writes them in that JSON and can pass it through, or read it
/// with {@link #parse(String)} to change it first; one generating them builds the classes and writes them with
/// {@link #toJSON(Map)}. The bootstrap service checks either against the domain at startup, see
/// `LayoutCheck`.
public final class Layouts
{
    private Layouts()
    {
    }


    /// Reads named layouts, keeping their order.
    ///
    /// @param json     a JSON map of layout name to layout
    ///
    /// @throws QLiveException for JSON that is no such map, naming the layout and node that isn't
    public static Map<String, Layout> parse(String json)
    {
        final Object parsed = JSONUtil.DEFAULT_PARSER.parse(json);
        if (!(parsed instanceof Map<?, ?> map))
        {
            throw new QLiveException("Named layouts are a JSON map of name to layout, not " + json);
        }

        final ConditionParser conditionParser = new ConditionParser();
        final Map<String, Layout> layouts = new LinkedHashMap<>();
        map.forEach((name, value) -> layouts.put((String) name, layout((String) name, value, conditionParser)));
        return layouts;
    }


    /// Writes named layouts as the JSON the frontend reads, the bootstrap's part of a `LayoutService`.
    public static JSONHolder toJSON(Map<String, Layout> layouts)
    {
        return new JSONHolder(layouts);
    }


    private static Layout layout(String name, Object value, ConditionParser conditionParser)
    {
        final Map<String, Object> map = object(value, "Layout " + name);
        final Layout layout = new Layout();
        layout.setDomainType(string(map, "domainType", "Layout " + name));
        layout.setNodes(nodes(map.get("nodes"), "Layout " + name, conditionParser));
        return layout;
    }


    private static List<LayoutNode> nodes(Object value, String where, ConditionParser conditionParser)
    {
        if (!(value instanceof List<?> list))
        {
            throw new QLiveException(where + " has no list of nodes.");
        }
        final List<LayoutNode> nodes = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++)
        {
            nodes.add(node(list.get(i), where + ", node " + i, conditionParser));
        }
        return nodes;
    }


    private static LayoutNode node(Object value, String where, ConditionParser conditionParser)
    {
        final Map<String, Object> map = object(value, where);

        if (map.containsKey("field"))
        {
            final LayoutFieldNode node = new LayoutFieldNode(string(map, "field", where));
            node.setWidget((String) map.get("widget"));
            node.setMode((String) map.get("mode"));
            node.setLabel((String) map.get("label"));
            final Object condition = map.get("condition");
            if (condition != null)
            {
                node.setCondition(conditionParser.parseCondition(JSONUtil.DEFAULT_GENERATOR.forValue(condition)));
            }
            return node;
        }
        if (map.containsKey("section"))
        {
            return new LayoutSectionNode(string(map, "section", where), nodes(map.get("nodes"), where, conditionParser));
        }
        if (map.containsKey("component"))
        {
            final LayoutComponentNode node = new LayoutComponentNode(string(map, "component", where));
            node.setProps(map.get("props") == null ? null : object(map.get("props"), where + ", props"));
            node.setNodes(map.get("nodes") == null ? null : nodes(map.get("nodes"), where, conditionParser));
            return node;
        }
        if (Boolean.TRUE.equals(map.get("status")))
        {
            return new LayoutStatusNode();
        }
        throw new QLiveException(
            where + " is no node: a node has a field, a section, a component or status true. " + map.keySet()
        );
    }


    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String where)
    {
        if (!(value instanceof Map<?, ?>))
        {
            throw new QLiveException(where + " is no JSON object: " + value);
        }
        return (Map<String, Object>) value;
    }


    private static String string(Map<String, Object> map, String key, String where)
    {
        if (!(map.get(key) instanceof String text))
        {
            throw new QLiveException(where + " has no " + key + ".");
        }
        return text;
    }
}
