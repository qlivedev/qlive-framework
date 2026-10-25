package io.github.qlivedev.qlive.model.layout;

import io.github.qlivedev.qlive.model.condition.CNode;
import jakarta.validation.constraints.NotNull;

/// What a reaction does to one field, by its path. The JSON tells the effects apart by the key each one has besides
/// `field`: `mode`, `value`, `effect`.
public sealed interface ReactionEffect
    permits ReactionEffect.Mode, ReactionEffect.Expression, ReactionEffect.FunctionReference
{
    /// The field the effect sets, by its path.
    @NotNull
    String getField();


    /// Sets the field's mode.
    final class Mode
        implements ReactionEffect
    {
        private final String field;

        private final String mode;


        public Mode(String field, String mode)
        {
            this.field = field;
            this.mode = mode;
        }


        @Override
        public String getField()
        {
            return field;
        }


        /// `normal`, `disabled`, `readOnly` or `text`.
        @NotNull
        public String getMode()
        {
            return mode;
        }
    }


    /// Sets the field's value to a FilterDSL expression over the form's row: a field, a value, or an operation on
    /// them.
    final class Expression
        implements ReactionEffect
    {
        private final String field;

        private final CNode value;


        public Expression(String field, CNode value)
        {
            this.field = field;
            this.value = value;
        }


        @Override
        public String getField()
        {
            return field;
        }


        @NotNull
        public CNode getValue()
        {
            return value;
        }
    }


    /// Sets the field's value to what a function of the frontend's returns, by the name it is registered under
    /// (`registerEffect()`): a lookup, postal code to city.
    final class FunctionReference
        implements ReactionEffect
    {
        private final String field;

        private final String effect;


        public FunctionReference(String field, String effect)
        {
            this.field = field;
            this.effect = effect;
        }


        @Override
        public String getField()
        {
            return field;
        }


        @NotNull
        public String getEffect()
        {
            return effect;
        }
    }
}
