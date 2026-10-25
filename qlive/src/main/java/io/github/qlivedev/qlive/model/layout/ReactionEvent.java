package io.github.qlivedev.qlive.model.layout;

import jakarta.validation.constraints.NotNull;
import org.svenson.JSONProperty;

/// What a reaction answers. The JSON tells the events apart by the key each one has, as it does layout nodes:
/// `init`, `change`, `blur`, `error`, `errorAt`.
public sealed interface ReactionEvent
    permits ReactionEvent.Init, ReactionEvent.Change, ReactionEvent.Blur, ReactionEvent.ErrorClass,
    ReactionEvent.ErrorAt
{
    /// The form shows a row, so that what the row opens with holds from the start.
    final class Init
        implements ReactionEvent
    {
        /// Always true; the key is what makes it this event.
        public boolean isInit()
        {
            return true;
        }
    }


    /// The form changed a field's value: the user, or a reaction.
    final class Change
        implements ReactionEvent
    {
        private final String change;


        public Change(String change)
        {
            this.change = change;
        }


        /// The field, by its path.
        @NotNull
        public String getChange()
        {
            return change;
        }
    }


    /// Focus left a field.
    final class Blur
        implements ReactionEvent
    {
        private final String blur;


        public Blur(String blur)
        {
            this.blur = blur;
        }


        /// The field, by its path.
        @NotNull
        public String getBlur()
        {
            return blur;
        }
    }


    /// An error of a class appeared, or cleared.
    final class ErrorClass
        implements ReactionEvent
    {
        private final String error;

        private final boolean cleared;


        public ErrorClass(String error, boolean cleared)
        {
            this.error = error;
            this.cleared = cleared;
        }


        /// The error class, as a rule or a refusal names it.
        @NotNull
        public String getError()
        {
            return error;
        }


        /// True for the error clearing, null for it appearing, which leaves the key out.
        @JSONProperty(ignoreIfNull = true)
        public Boolean getCleared()
        {
            return cleared ? true : null;
        }
    }


    /// An error blaming a field, or one below it, appeared or cleared.
    final class ErrorAt
        implements ReactionEvent
    {
        private final String errorAt;

        private final boolean cleared;


        public ErrorAt(String errorAt, boolean cleared)
        {
            this.errorAt = errorAt;
            this.cleared = cleared;
        }


        /// The field, by its path.
        @NotNull
        public String getErrorAt()
        {
            return errorAt;
        }


        /// True for the error clearing, null for it appearing, which leaves the key out.
        @JSONProperty(ignoreIfNull = true)
        public Boolean getCleared()
        {
            return cleared ? true : null;
        }
    }
}
