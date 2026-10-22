package io.github.qlivedev.qlive.graphql.logic;

import jakarta.validation.constraints.NotNull;

/// A reference to one row an action is about: its id, and the version the user saw it at.
///
/// What a subject argument of an action mutation takes in place of the row itself. The framework loads the row
/// and refuses the action with a conflict where the stored version is no longer this one.
public class SubjectRef
{
    private String id;

    private String version;


    public SubjectRef()
    {
    }


    public SubjectRef(String id, String version)
    {
        this.id = id;
        this.version = version;
    }


    @NotNull
    public String getId()
    {
        return id;
    }


    public void setId(String id)
    {
        this.id = id;
    }


    public String getVersion()
    {
        return version;
    }


    public void setVersion(String version)
    {
        this.version = version;
    }


    @Override
    public String toString()
    {
        return "SubjectRef(" + id + ", " + version + ")";
    }
}
