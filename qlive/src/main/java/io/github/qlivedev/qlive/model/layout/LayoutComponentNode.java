package io.github.qlivedev.qlive.model.layout;

import jakarta.validation.constraints.NotNull;
import org.svenson.JSONProperty;

import java.util.List;
import java.util.Map;

/// A component of the application's, with the nodes as its children.
public final class LayoutComponentNode
    implements LayoutNode
{
    private String component;

    private Map<String, Object> props;

    private List<LayoutNode> nodes;


    public LayoutComponentNode()
    {
    }


    public LayoutComponentNode(String component)
    {
        this.component = component;
    }


    /// The name the component is registered under in the frontend (`registerComponent()`).
    @NotNull
    public String getComponent()
    {
        return component;
    }


    public void setComponent(String component)
    {
        this.component = component;
    }


    @JSONProperty(ignoreIfNull = true)
    public Map<String, Object> getProps()
    {
        return props;
    }


    public void setProps(Map<String, Object> props)
    {
        this.props = props;
    }


    @JSONProperty(ignoreIfNull = true)
    public List<LayoutNode> getNodes()
    {
        return nodes;
    }


    public void setNodes(List<LayoutNode> nodes)
    {
        this.nodes = nodes;
    }
}
