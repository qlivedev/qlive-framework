package io.github.qlivedev.model.layout;

/// One node of a layout: a field, a section of nodes, a component of the application's, or the place of the
/// form's live region. The JSON tells them apart by the key each one has and the others don't -- `field`,
/// `section`, `component`, `status` -- not by a discriminator.
public sealed interface LayoutNode
    permits LayoutFieldNode, LayoutSectionNode, LayoutComponentNode, LayoutStatusNode
{
}
