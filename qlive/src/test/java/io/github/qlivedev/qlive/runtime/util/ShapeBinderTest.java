package io.github.qlivedev.qlive.runtime.util;

import io.github.qlivedev.qlive.api.json.JSONUtil;
import io.github.qlivedev.qlive.runtime.QLiveException;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShapeBinderTest
{
    public sealed interface Shape
        permits Circle, Square, Point
    {
    }


    public static final class Circle
        implements Shape
    {
        private final double radius;

        private String color;


        public Circle(double radius)
        {
            this.radius = radius;
        }


        @NotNull
        public Double getRadius()
        {
            return radius;
        }


        public String getColor()
        {
            return color;
        }


        public void setColor(String color)
        {
            this.color = color;
        }
    }


    public static final class Square
        implements Shape
    {
        private final int side;

        private final boolean filled;


        public Square(int side, boolean filled)
        {
            this.side = side;
            this.filled = filled;
        }


        @NotNull
        public Integer getSide()
        {
            return side;
        }


        public boolean isFilled()
        {
            return filled;
        }
    }


    public static final class Point
        implements Shape
    {
        public boolean isPoint()
        {
            return true;
        }
    }


    public enum Unit
    {
        MM, CM
    }


    public static class Drawing
    {
        private final String title;

        private List<Shape> shapes;

        private Map<String, Shape> named;

        private Unit unit;

        private Map<String, Object> props;


        public Drawing(String title)
        {
            this.title = title;
        }


        @NotNull
        public String getTitle()
        {
            return title;
        }


        @NotNull
        public List<Shape> getShapes()
        {
            return shapes;
        }


        public void setShapes(List<Shape> shapes)
        {
            this.shapes = shapes;
        }


        public Map<String, Shape> getNamed()
        {
            return named;
        }


        public void setNamed(Map<String, Shape> named)
        {
            this.named = named;
        }


        public Unit getUnit()
        {
            return unit;
        }


        public void setUnit(Unit unit)
        {
            this.unit = unit;
        }


        public Map<String, Object> getProps()
        {
            return props;
        }


        public void setProps(Map<String, Object> props)
        {
            this.props = props;
        }
    }


    private final ShapeBinder binder = new ShapeBinder();


    private static Object tree(String json)
    {
        return JSONUtil.DEFAULT_PARSER.parse(json);
    }


    @Test
    void tellsUnionMembersApartByTheirKeys()
    {
        final Drawing drawing = binder.bind(
            Drawing.class,
            tree("{\"title\":\"t\",\"unit\":\"CM\",\"shapes\":[" +
                "{\"radius\":2,\"color\":\"red\"},{\"side\":3,\"filled\":true},{\"side\":4},{\"point\":true}" +
                "],\"named\":{\"dot\":{\"point\":true}},\"props\":{\"any\":[1,{\"x\":\"y\"}]}}"),
            "Drawing"
        );

        assertThat(drawing.getTitle(), is("t"));
        assertThat(drawing.getUnit(), is(Unit.CM));

        final Circle circle = (Circle) drawing.getShapes().get(0);
        // an integral number where a double is expected, and a setter beside the constructor
        assertThat(circle.getRadius(), is(2.0));
        assertThat(circle.getColor(), is("red"));

        assertThat(((Square) drawing.getShapes().get(1)).isFilled(), is(true));
        // a primitive parameter without its key gets its default
        assertThat(((Square) drawing.getShapes().get(2)).isFilled(), is(false));

        assertThat(drawing.getShapes().get(3), instanceOf(Point.class));
        assertThat(drawing.getNamed().get("dot"), instanceOf(Point.class));
        // untyped values stay as the tree has them
        assertThat(drawing.getProps().get("any"), is(List.of(1L, Map.of("x", "y"))));
    }


    @Test
    void namesWhatIsNoMember()
    {
        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> binder.bind(Drawing.class, tree("{\"title\":\"t\",\"shapes\":[{\"radius\":1},{\"color\":\"red\"}]}"), "Drawing")
        );
        assertThat(
            e.getMessage(),
            is("Drawing.shapes[1] is no Shape, which has a radius, a side or point true. It has [color]")
        );
    }


    @Test
    void takesAConstantOnlyWithItsValue()
    {
        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> binder.bind(Shape.class, tree("{\"point\":false}"), "Shape")
        );
        assertThat(e.getMessage(), containsString("Shape is no Shape"));
    }


    @Test
    void refusesAnObjectOfTwoMembers()
    {
        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> binder.bind(Shape.class, tree("{\"radius\":1,\"side\":2}"), "Shape")
        );
        assertThat(e.getMessage(), containsString("Shape is both a Circle and a Square"));
    }


    @Test
    void namesAMissingRequiredKey()
    {
        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> binder.bind(Drawing.class, tree("{\"title\":\"t\"}"), "Drawing")
        );
        assertThat(e.getMessage(), is("Drawing has no shapes."));
    }


    @Test
    void namesAValueOfTheWrongType()
    {
        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> binder.bind(Shape.class, tree("{\"side\":2.5}"), "Shape")
        );
        assertThat(e.getMessage(), is("Shape.side is no Integer: 2.5"));
    }


    public sealed interface Vague
        permits Left, Right
    {
    }


    public static final class Left
        implements Vague
    {
        private String name;


        @NotNull
        public String getName()
        {
            return name;
        }


        public void setName(String name)
        {
            this.name = name;
        }
    }


    public static final class Right
        implements Vague
    {
        private String name;


        @NotNull
        public String getName()
        {
            return name;
        }


        public void setName(String name)
        {
            this.name = name;
        }
    }


    @Test
    void refusesAUnionWhoseMembersShareTheirKeys()
    {
        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> binder.bind(Vague.class, tree("{\"name\":\"n\"}"), "Vague")
        );
        assertThat(e.getMessage(), containsString("Left of Vague has no key of its own"));
    }


    public static class Labeled
    {
        private Object label;


        public Object getLabel()
        {
            return label;
        }


        public void setLabel(Object label)
        {
            this.label = label;
        }
    }


    public static class Holder
    {
        private CharSequence text;

        private List<Labeled> labeled;


        public CharSequence getText()
        {
            return text;
        }


        public void setText(CharSequence text)
        {
            this.text = text;
        }


        public List<Labeled> getLabeled()
        {
            return labeled;
        }


        public void setLabeled(List<Labeled> labeled)
        {
            this.labeled = labeled;
        }
    }


    @Test
    void handsATypeWithAParserItsPartOfTheTree()
    {
        final ShapeBinder binder = new ShapeBinder().withParser(CharSequence.class, value -> "<" + value + ">");

        final Holder holder = binder.bind(Holder.class, tree("{\"text\":\"a\",\"labeled\":[{\"label\":\"b\"}]}"), "Holder");

        assertThat(holder.getText(), is("<a>"));
        assertThat(holder.getLabeled().get(0).getLabel(), is("b"));
    }


    @Test
    void namesWhatAParserRefuses()
    {
        final ShapeBinder binder = new ShapeBinder().withParser(
            CharSequence.class, value -> {
                throw new IllegalStateException("no text");
            }
        );

        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> binder.bind(Holder.class, tree("{\"text\":\"a\"}"), "Holder")
        );
        assertThat(e.getMessage(), is("Holder.text is no CharSequence: no text"));
    }


    @Test
    void leavesWhatIsAbsentUnset()
    {
        final Holder holder = binder.bind(Holder.class, tree("{\"unknown\":1}"), "Holder");

        assertThat(holder.getText(), is(nullValue()));
        assertThat(holder.getLabeled(), is(nullValue()));
    }


    @Test
    void bindsAListOfAUnion()
    {
        final Object shapes = binder.bind(listOfShapes(), tree("[{\"point\":true},{\"radius\":1}]"), "shapes");

        assertThat((List<?>) shapes, contains(instanceOf(Point.class), instanceOf(Circle.class)));
    }


    private static java.lang.reflect.Type listOfShapes()
    {
        try
        {
            return Drawing.class.getMethod("setShapes", List.class).getGenericParameterTypes()[0];
        }
        catch (NoSuchMethodException e)
        {
            throw new IllegalStateException(e);
        }
    }
}
