package io.github.qlivedev.qlive.model.layout;

import jakarta.validation.constraints.NotNull;
import org.svenson.JSONProperty;

import java.util.ArrayList;
import java.util.List;

/// What a form renders for one entity: its fields, grouped and arranged by the nodes. The frontend's `Layout`, in
/// the same JSON, which {@link Layouts} reads and writes.
public class Layout
{
    private String name;

    private String domainType;

    private List<LayoutNode> nodes = new ArrayList<>();

    private List<Reaction> reactions;


    public Layout()
    {
    }


    public Layout(String domainType, List<LayoutNode> nodes)
    {
        this.domainType = domainType;
        this.nodes = nodes;
    }


    /// The name overrides match it by. Where a layout is one of a map of named layouts, the map's key names it,
    /// and this is left out.
    @JSONProperty(ignoreIfNull = true)
    public String getName()
    {
        return name;
    }


    public void setName(String name)
    {
        this.name = name;
    }


    /// The type of the row the form edits.
    @NotNull
    public String getDomainType()
    {
        return domainType;
    }


    public void setDomainType(String domainType)
    {
        this.domainType = domainType;
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


    /// What the form does when a field changes or loses focus, or an error appears; null for nothing.
    @JSONProperty(ignoreIfNull = true)
    public List<Reaction> getReactions()
    {
        return reactions;
    }


    public void setReactions(List<Reaction> reactions)
    {
        this.reactions = reactions;
    }
}
