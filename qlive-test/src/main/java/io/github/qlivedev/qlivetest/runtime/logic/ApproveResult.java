package io.github.qlivedev.qlivetest.runtime.logic;

/// What approving a bar answers with.
public class ApproveResult
{
    private String barId;

    private String name;

    private String comment;

    private boolean notified;


    public ApproveResult()
    {
    }


    public ApproveResult(String barId, String name, String comment, boolean notified)
    {
        this.barId = barId;
        this.name = name;
        this.comment = comment;
        this.notified = notified;
    }


    /// Id of the approved bar
    public String getBarId()
    {
        return barId;
    }


    public void setBarId(String barId)
    {
        this.barId = barId;
    }


    /// Name of the approved bar, as it was stored when the action ran
    public String getName()
    {
        return name;
    }


    public void setName(String name)
    {
        this.name = name;
    }


    /// The approval comment
    public String getComment()
    {
        return comment;
    }


    public void setComment(String comment)
    {
        this.comment = comment;
    }


    /// Whether the owner was notified
    public boolean isNotified()
    {
        return notified;
    }


    public void setNotified(boolean notified)
    {
        this.notified = notified;
    }
}
