package io.github.qlivedev.model.layout;

import io.github.qlivedev.model.condition.ConditionParser;
import io.github.qlivedev.runtime.QLiveException;
import io.github.qlivedev.util.JSONUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Named layouts, in one namespace: what a `LayoutProvider` returns, and what the bootstrap sends as the JSON map
/// of name to layout the frontend reads.
///
/// A layout put under a name a layout already has replaces it. That is how several providers share the namespace,
/// a later one changing what an earlier one gave, and how the frontend's init hook treats the layouts the bootstrap
/// brought.
public final class Layouts
{
    private final Map<String, Layout> layouts = new LinkedHashMap<>();


    /// Puts the layout under the name, replacing the one there was.
    ///
    /// @return this, to put another
    public Layouts put(String name, Layout layout)
    {
        layouts.put(name, layout);
        return this;
    }


    /// Puts every layout of the given ones, replacing those of the same names.
    ///
    /// @return this
    public Layouts putAll(Layouts other)
    {
        layouts.putAll(other.layouts);
        return this;
    }


    /// The layout of the name, null for a name none has.
    public Layout get(String name)
    {
        return layouts.get(name);
    }


    /// The layouts by name, in the order they were put.
    public Map<String, Layout> asMap()
    {
        return Collections.unmodifiableMap(layouts);
    }


    /// Reads named layouts from the JSON the frontend reads, keeping their order: a map of name to layout, the
    /// nodes told apart by the key each one has -- `field`, `section`, `component`, `status`. A layout's own name
    /// is ignored; the key names it.
    ///
    /// @throws QLiveException for JSON that is no such map, naming the layout and node that isn't
    public static Layouts parse(String json)
    {
        final Object parsed = JSONUtil.DEFAULT_PARSER.parse(json);
        if (!(parsed instanceof Map<?, ?> map))
        {
            throw new QLiveException("Named layouts are a JSON map of name to layout, not " + json);
        }

        final ConditionParser conditionParser = new ConditionParser();
        final Layouts layouts = new Layouts();
        map.forEach((name, value) -> layouts.put((String) name, layout((String) name, value, conditionParser)));
        return layouts;
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
