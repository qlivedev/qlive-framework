package io.github.qlivedev.qlive.runtime.scalar;

public enum NodeType
{
    CONDITION("Condition"),
    FIELD_REFERENCE("FieldReference"),
    VALUE("Value"),
    VALUES("Values"),
    OPERATION("Operation"),
    COMPONENT("Component");

    private final String name;


    NodeType(String name)
    {
        this.name = name;
    }

    public String getName()
    {
        return name;
    }

    public static NodeType forName(String name)
    {
        for (NodeType type : values())
        {
            if (type.name.equals(name))
            {
                return type;
            }
        }
        throw new IllegalArgumentException("No node type " + name);
    }
}
