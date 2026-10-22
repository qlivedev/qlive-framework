package io.github.qlivedev.qlive.model.layout;

import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

/// A labeled group of nodes, a `<fieldset>` whose legend is the section as given: text as the page shows it, so
/// translated into the page's locale by whoever delivers the layout.
public final class LayoutSectionNode
    implements LayoutNode
{
    private String section;

    private List<LayoutNode> nodes = new ArrayList<>();


    public LayoutSectionNode()
    {
    }


    public LayoutSectionNode(String section, List<LayoutNode> nodes)
    {
        this.section = section;
        this.nodes = nodes;
    }


    @NotNull
    public String getSection()
    {
        return section;
    }


    public void setSection(String section)
    {
        this.section = section;
    }


    @NotNull
    public List<LayoutNode> getNodes()
    {
        return nodes;
    }


    public void setNodes(List<LayoutNode> nodes)
    {
        this.nodes = nodes;
    }
}
