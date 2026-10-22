package io.github.qlivedev.qlive.model.layout;

import io.github.qlivedev.qlive.model.condition.Condition;
import jakarta.validation.constraints.NotNull;
import org.svenson.JSONProperty;

/// One field, which the frontend renders with `Field` and these options.
public final class LayoutFieldNode
    implements LayoutNode
{
    private String field;

    private String widget;

    private String mode;

    private String label;

    private Condition condition;


    public LayoutFieldNode()
    {
    }


    public LayoutFieldNode(String field)
    {
        this.field = field;
    }


    /// The field of the form's row, by its name: a scalar, a to-one relation by its object, a many-to-many field.
    @NotNull
    public String getField()
    {
        return field;
    }


    public void setField(String field)
    {
        this.field = field;
    }


    /// The name the widget is registered under in the frontend (`registerWidget()`); the type's own without one.
    @JSONProperty(ignoreIfNull = true)
    public String getWidget()
    {
        return widget;
    }


    public void setWidget(String widget)
    {
        this.widget = widget;
    }


    /// The mode the field starts in, `normal`, `disabled`, `readOnly` or `text`; the type's default without one.
    @JSONProperty(ignoreIfNull = true)
    public String getMode()
    {
        return mode;
    }


    public void setMode(String mode)
    {
        this.mode = mode;
    }


    /// The label as shown, in place of the field's translation.
    @JSONProperty(ignoreIfNull = true)
    public String getLabel()
    {
        return label;
    }


    public void setLabel(String label)
    {
        this.label = label;
    }


    /// Which related rows a to-one relation offers, written against the form's row with the candidate in the
    /// relation's place.
    @JSONProperty(ignoreIfNull = true)
    public Condition getCondition()
    {
        return condition;
    }


    public void setCondition(Condition condition)
    {
        this.condition = condition;
    }
}
