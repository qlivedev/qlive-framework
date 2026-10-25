package io.github.qlivedev.qlive.model.layout;

import io.github.qlivedev.qlive.model.condition.Condition;
import jakarta.validation.constraints.NotNull;
import org.svenson.JSONProperty;

import java.util.ArrayList;
import java.util.List;

/// An event wired to effects: what a form does when a field changes or loses focus, or an error appears, to help
/// the user bring the row into compliance with the rules. The frontend's `Reaction`, in the same JSON.
///
/// The server never runs a reaction. It reaches the server only through the values it sets, which the merge checks
/// like any edit.
public final class Reaction
{
    private List<ReactionEvent> on = new ArrayList<>();

    private Condition when;

    private List<ReactionEffect> effects = new ArrayList<>();

    private List<ReactionEffect> otherwise;


    public Reaction()
    {
    }


    public Reaction(List<ReactionEvent> on, List<ReactionEffect> effects)
    {
        this.on = on;
        this.effects = effects;
    }


    /// The events the reaction answers, any of them.
    @NotNull
    public List<ReactionEvent> getOn()
    {
        return on;
    }


    public void setOn(List<ReactionEvent> on)
    {
        this.on = on;
    }


    /// The condition on the form's row under which the reaction has its `effects`; where it doesn't hold, the
    /// reaction has those `otherwise`. A reaction without a condition always has its `effects`.
    @JSONProperty(ignoreIfNull = true)
    public Condition getWhen()
    {
        return when;
    }


    public void setWhen(Condition when)
    {
        this.when = when;
    }


    /// What the reaction does, in order.
    @NotNull
    public List<ReactionEffect> getEffects()
    {
        return effects;
    }


    public void setEffects(List<ReactionEffect> effects)
    {
        this.effects = effects;
    }


    /// What the reaction does where `when` doesn't hold.
    @JSONProperty(ignoreIfNull = true)
    public List<ReactionEffect> getOtherwise()
    {
        return otherwise;
    }


    public void setOtherwise(List<ReactionEffect> otherwise)
    {
        this.otherwise = otherwise;
    }
}
