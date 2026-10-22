package io.github.qlivedev.qlive.model.layout;

/// Where the form's live region goes, see the frontend's `FormStatus`. Without one it is at the top of the form.
public final class LayoutStatusNode
    implements LayoutNode
{
    /// Always true; the key is what makes it this node.
    public boolean isStatus()
    {
        return true;
    }
}
