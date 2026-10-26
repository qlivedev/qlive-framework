package io.github.qlivedev.qlive.model.layout;

import io.github.qlivedev.qlive.model.condition.CNode;
import io.github.qlivedev.qlive.model.condition.ConditionParser;
import io.github.qlivedev.qlive.runtime.QLiveException;
import io.github.qlivedev.qlive.api.json.JSONUtil;
import io.github.qlivedev.qlive.runtime.util.ShapeBinder;

import java.util.Collections;
import java.util.LinkedHashMap;
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
    /// nodes, events and effects told apart by the key each one has, see {@link ShapeBinder}. A layout's own name
    /// is ignored; the key names it.
    ///
    /// @throws QLiveException for JSON that is no such map, naming the path to the part that isn't
    public static Layouts parse(String json)
    {
        final Object parsed = JSONUtil.DEFAULT_PARSER.parse(json);
        if (!(parsed instanceof Map<?, ?> map))
        {
            throw new QLiveException("Named layouts are a JSON map of name to layout, not " + json);
        }

        final ConditionParser conditionParser = new ConditionParser();
        final ShapeBinder binder = new ShapeBinder()
            .withParser(CNode.class, value -> conditionParser.parse(JSONUtil.DEFAULT_GENERATOR.forValue(value)));

        final Layouts layouts = new Layouts();
        map.forEach((name, value) -> {
            final Layout layout = binder.bind(Layout.class, value, "Layout " + name);
            layout.setName(null);
            layouts.put((String) name, layout);
        });
        return layouts;
    }
}
