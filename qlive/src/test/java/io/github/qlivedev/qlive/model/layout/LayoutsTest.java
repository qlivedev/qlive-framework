package io.github.qlivedev.qlive.model.layout;

import io.github.qlivedev.qlive.model.condition.FieldReference;
import io.github.qlivedev.qlive.runtime.QLiveException;
import io.github.qlivedev.qlive.api.json.JSONUtil;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LayoutsTest
{
    private final static String JSON = "{\"fooEdit\":{\"domainType\":\"Foo\",\"nodes\":[" +
        "{\"section\":\"Details\",\"nodes\":[" +
        "{\"field\":\"name\",\"mode\":\"readOnly\",\"label\":\"Title\"}," +
        "{\"field\":\"owner\",\"widget\":\"userPicker\",\"condition\":" +
        "{\"type\":\"Condition\",\"name\":\"eq\",\"operands\":[" +
        "{\"type\":\"FieldReference\",\"name\":\"flag\"},{\"type\":\"Value\",\"scalarType\":\"Boolean\",\"value\":true}" +
        "]}}" +
        "]}," +
        "{\"component\":\"Box\",\"props\":{\"wide\":true},\"nodes\":[{\"field\":\"num\"}]}," +
        "{\"status\":true}" +
        "]}}";


    @Test
    void readsEveryKindOfNode()
    {
        final Layout layout = Layouts.parse(JSON).get("fooEdit");

        assertThat(layout.getDomainType(), is("Foo"));
        final LayoutSectionNode section = (LayoutSectionNode) layout.getNodes().get(0);
        assertThat(section.getSection(), is("Details"));

        final LayoutFieldNode name = (LayoutFieldNode) section.getNodes().get(0);
        assertThat(name.getMode(), is("readOnly"));
        assertThat(name.getLabel(), is("Title"));
        assertThat(name.getCondition(), is(nullValue()));

        final LayoutFieldNode owner = (LayoutFieldNode) section.getNodes().get(1);
        assertThat(owner.getWidget(), is("userPicker"));
        assertThat(owner.getCondition().getOperands().get(0), instanceOf(FieldReference.class));

        final LayoutComponentNode box = (LayoutComponentNode) layout.getNodes().get(1);
        assertThat(box.getProps(), is(Map.of("wide", true)));
        assertThat(((LayoutFieldNode) box.getNodes().get(0)).getField(), is("num"));

        assertThat(layout.getNodes().get(2), instanceOf(LayoutStatusNode.class));
    }


    @Test
    void writesWhatItReads()
    {
        final String written = json(Layouts.parse(JSON));

        assertThat(json(Layouts.parse(written)), is(written));
        // what is absent stays absent, rather than arriving as null
        assertThat(written, containsString("{\"field\":\"num\"}"));
        assertThat(written, containsString("{\"status\":true}"));
    }


    @Test
    void writesLayoutsBuiltInJava()
    {
        final Layouts layouts = new Layouts()
            .put("fooName", new Layout("Foo", List.of(new LayoutFieldNode("name"), new LayoutStatusNode())));

        assertThat(
            json(layouts),
            is("{\"fooName\":{\"domainType\":\"Foo\",\"nodes\":[{\"field\":\"name\"},{\"status\":true}]}}")
        );
    }


    @Test
    void replacesALayoutPutUnderANameAgain()
    {
        final Layout first = new Layout("Foo", List.of(new LayoutFieldNode("name")));
        final Layout second = new Layout("Foo", List.of(new LayoutFieldNode("num")));
        final Layout other = new Layout("Bar", List.of(new LayoutFieldNode("name")));

        final Layouts layouts = new Layouts().put("foo", first).put("bar", other)
            .putAll(new Layouts().put("foo", second));

        assertThat(layouts.get("foo"), is(sameInstance(second)));
        assertThat(layouts.asMap().keySet(), contains("foo", "bar"));
    }


    /// what the bootstrap sends
    private static String json(Layouts layouts)
    {
        return JSONUtil.DEFAULT_GENERATOR.forValue(layouts.asMap());
    }


    @Test
    void namesWhatIsNoNode()
    {
        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> Layouts.parse("{\"x\":{\"domainType\":\"Foo\",\"nodes\":[{\"field\":\"a\"},{\"widget\":\"w\"}]}}")
        );
        assertThat(e.getMessage(), containsString("Layout x, node 1 is no node"));
    }
}
