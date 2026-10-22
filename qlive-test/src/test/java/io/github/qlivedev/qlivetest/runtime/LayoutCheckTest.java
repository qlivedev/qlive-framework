package io.github.qlivedev.qlivetest.runtime;

import io.github.qlivedev.qlive.graphql.QLiveDomain;
import io.github.qlivedev.qlive.model.condition.Condition;
import io.github.qlivedev.qlive.model.condition.FieldReference;
import io.github.qlivedev.qlive.model.condition.Value;
import io.github.qlivedev.qlive.model.layout.Layout;
import io.github.qlivedev.qlive.model.layout.LayoutFieldNode;
import io.github.qlivedev.qlive.model.layout.LayoutNode;
import io.github.qlivedev.qlive.model.layout.LayoutSectionNode;
import io.github.qlivedev.qlive.model.layout.Layouts;
import io.github.qlivedev.qlive.runtime.QLiveException;
import io.github.qlivedev.qlive.runtime.layout.LayoutCheck;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// The layout checks against this application's domain: Foo with its to-one relations owner and fooType, Bar with
/// its many-to-many field bazes, AppUser with the list of the foos it owns.
@SpringBootTest
class LayoutCheckTest
{
    @Autowired
    private QLiveDomain domain;


    private static LayoutFieldNode field(String path, Condition condition)
    {
        final LayoutFieldNode node = new LayoutFieldNode(path);
        node.setCondition(condition);
        return node;
    }


    private static Condition reads(String path)
    {
        return new FieldReference(path).eq(new Value("String", "x"));
    }


    private List<String> problems(String type, LayoutNode... nodes)
    {
        return new LayoutCheck(domain).problems("test", new Layout(type, List.of(nodes)));
    }


    @Test
    void acceptsWhatAFormEdits()
    {
        assertThat(
            problems(
                "Foo",
                new LayoutSectionNode("Details", List.of(new LayoutFieldNode("name"), new LayoutFieldNode("num"))),
                field("owner", reads("owner.login")),
                field("fooType", reads("fooType.name")),
                field("fooType", reads("flag"))
            ),
            is(List.of())
        );
        assertThat(problems("Bar", new LayoutFieldNode("bazes")), is(List.of()));
    }


    @Test
    void namesEveryReferenceThatDoesNotHold()
    {
        final LayoutFieldNode badMode = new LayoutFieldNode("name");
        badMode.setMode("hidden");

        assertThat(
            problems(
                "Foo",
                new LayoutFieldNode("nope"),
                new LayoutSectionNode("Inside", List.of(new LayoutFieldNode("owner.login"))),
                badMode,
                field("num", reads("flag")),
                field("owner", reads("owner.nope")),
                field("owner", reads("flag.x"))
            ),
            contains(
                "Layout test, field nope of Foo: no such field.",
                "Layout test, field owner.login of Foo: a form edits its own row's fields; a related row's are a " +
                    "field group's.",
                "Layout test, field name of Foo: no mode hidden, only normal, disabled, readOnly, text.",
                "Layout test, field num of Foo: a condition narrows the rows a to-one relation offers, and this is " +
                    "none.",
                "Layout test, field owner of Foo: the condition reads owner.nope, but AppUser has no field nope.",
                "Layout test, field owner of Foo: the condition reads flag.x, but flag is no to-one relation of Foo."
            )
        );
        assertThat(problems("Nope"), is(List.of("Layout test: Nope is no domain type.")));
    }


    @Test
    void refusesTheOtherSideOfAToOneRelation()
    {
        // the foos a user owns: a list, but Foo.owner is what a form edits
        assertThat(
            problems("AppUser", new LayoutFieldNode("foos")),
            is(List.of("Layout test, field foos of AppUser: a list, and not a many-to-many field."))
        );
    }


    @Test
    void failsOnNamedLayoutsWithEveryProblem()
    {
        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> new LayoutCheck(domain).check(new Layouts()
                .put("a", new Layout("Foo", List.of(new LayoutFieldNode("nope"))))
                .put("b", new Layout("Bar", List.of(new LayoutFieldNode("name"))))
            )
        );
        assertThat(e.getMessage(), containsString("Layout a, field nope of Foo: no such field."));
    }}
